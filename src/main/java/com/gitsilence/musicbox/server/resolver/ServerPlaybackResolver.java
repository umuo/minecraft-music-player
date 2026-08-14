package com.gitsilence.musicbox.server.resolver;

import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.playback.TrackRef;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ServerPlaybackResolver {
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final HttpClient client;
    private final URI endpoint;

    public ServerPlaybackResolver() {
        endpoint = ResolverSecurityPolicy.validateEndpoint(MusicBoxConfig.PLAYBACK_API_URL.get(), MusicBoxConfig.REQUIRE_HTTPS.get());
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout()).build();
    }

    public CompletableFuture<ResolvedTrack> resolve(TrackRef track) {
        return request(track, "musicUrl").thenCombine(request(track, "lyric"), (audio, lyric) -> {
            URI uri = ResolverSecurityPolicy.validateAudio(ResolverResponseParser.audioUrl(audio.status(), audio.body()),
                    MusicBoxConfig.ALLOWED_AUDIO_HOSTS.get(), MusicBoxConfig.REQUIRE_HTTPS.get());
            String lrc = ResolverResponseParser.lyrics(lyric.status(), lyric.body());
            if (lrc.length() > 200_000) lrc = lrc.substring(0, 200_000);
            return new ResolvedTrack(uri.toASCIIString(), lrc);
        });
    }

    private CompletableFuture<Response> request(TrackRef track, String action) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout()).header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson(track, action).toString()));
        String token = MusicBoxConfig.PLAYBACK_API_TOKEN.get().strip();
        if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream stream = response.body()) {
                if (response.statusCode() >= 300 && response.statusCode() < 400)
                    throw new IllegalStateException("Playback API redirects are forbidden");
                String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!type.contains("json") && !type.startsWith("text/plain"))
                    throw new IllegalStateException("Playback API must return JSON");
                byte[] body = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("Playback API response exceeds 1 MiB");
                return new Response(response.statusCode(), new String(body, StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
    }

    static JsonObject requestJson(TrackRef track, String action) {
        JsonObject music = new JsonObject();
        music.addProperty("id", track.trackId()); music.addProperty("songmid", track.trackId());
        music.addProperty("name", track.title()); music.addProperty("singer", track.artist());
        music.addProperty("albumName", track.album()); music.addProperty("interval", track.durationSeconds());
        music.addProperty("hash", track.hashForQuality("128k"));
        JsonObject types = new JsonObject();
        addType(types, "128k", track.hash128()); addType(types, "320k", track.hash320());
        addType(types, "flac", track.hashFlac()); addType(types, "flac24bit", track.hashHires()); music.add("_types", types);
        JsonObject info = new JsonObject(); info.addProperty("type", track.quality()); info.add("musicInfo", music);
        JsonObject root = new JsonObject(); root.addProperty("source", track.source()); root.addProperty("action", action); root.add("info", info);
        return root;
    }
    private static void addType(JsonObject types, String name, String hash) {
        if (hash == null || hash.isBlank()) return; JsonObject value = new JsonObject(); value.addProperty("hash", hash); types.add(name, value);
    }
    private static Duration timeout() { return Duration.ofSeconds(MusicBoxConfig.HTTP_TIMEOUT_SECONDS.getAsInt()); }
    private record Response(int status, String body) { }
    public record ResolvedTrack(String audioUrl, String lyrics) { }
}
