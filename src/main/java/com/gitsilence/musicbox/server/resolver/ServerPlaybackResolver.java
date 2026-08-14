package com.gitsilence.musicbox.server.resolver;

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
    private final ResolverSourceConfig source;

    public ServerPlaybackResolver(ResolverSourceConfig source) {
        this.source = source;
        endpoint = ResolverSecurityPolicy.validateEndpoint(source.playbackApiUrl(), source.requireHttps());
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout()).build();
    }

    public CompletableFuture<ResolvedTrack> resolve(TrackRef track) {
        CompletableFuture<Response> lyricRequest = source.capabilities().contains("lyric")
                ? request(track, "lyric") : CompletableFuture.completedFuture(new Response(200, "{\"lyric\":\"\"}"));
        return request(track, "musicUrl").thenCombine(lyricRequest, (audio, lyric) -> {
            URI uri = ResolverSecurityPolicy.validateAudio(ResolverResponseParser.audioUrl(audio.status(), audio.body()),
                    source.allowedAudioHosts(), source.requireHttps());
            String lrc = ResolverResponseParser.lyrics(lyric.status(), lyric.body());
            if (lrc.length() > 200_000) lrc = lrc.substring(0, 200_000);
            return new ResolvedTrack(uri.toASCIIString(), lrc);
        });
    }

    private CompletableFuture<Response> request(TrackRef track, String action) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout()).header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson(track, action).toString()));
        String token = source.token().strip();
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
        // LX sources use different names for the same catalog identity. Supplying aliases keeps the normalized
        // TrackRef compatible with tx/kw/mg handlers without changing or guessing the platform id.
        music.addProperty("mid", track.trackId()); music.addProperty("musicrid", track.trackId());
        music.addProperty("copyrightId", track.trackId()); music.addProperty("contentId", track.trackId());
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
    private Duration timeout() { return Duration.ofSeconds(source.timeoutSeconds()); }
    private record Response(int status, String body) { }
    public record ResolvedTrack(String audioUrl, String lyrics) { }
}
