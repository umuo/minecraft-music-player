package com.gitsilence.musicbox.client.api;

import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.playback.TrackRef;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class LxPlaybackResolver {
    private final HttpClient httpClient;

    public LxPlaybackResolver() {
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout())
                .build();
    }

    public CompletableFuture<URI> resolve(TrackRef track) {
        URI endpoint = parseEndpoint();
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson(track).toString()));
        String token = MusicBoxConfig.PLAYBACK_API_TOKEN.get().strip();
        if (!token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }

        return httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(endpoint, response));
    }

    public HttpResponse<java.io.InputStream> openAudio(URI audioUri) throws IOException, InterruptedException {
        validateAudioUri(parseEndpoint(), audioUri);
        HttpRequest request = HttpRequest.newBuilder(audioUri)
                .timeout(timeout())
                .header("Accept", "audio/mpeg,audio/*;q=0.9,*/*;q=0.1")
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Audio server returned HTTP " + response.statusCode());
        }
        return response;
    }

    private static URI parseEndpoint() {
        String value = MusicBoxConfig.PLAYBACK_API_URL.get().strip();
        if (value.isEmpty()) {
            throw new IllegalStateException("The playback API URL is not configured");
        }
        URI endpoint = URI.create(value);
        validateHttpUri(endpoint, "playback API");
        return endpoint;
    }

    private static JsonObject requestJson(TrackRef track) {
        JsonObject musicInfo = new JsonObject();
        musicInfo.addProperty("id", track.trackId());
        musicInfo.addProperty("songmid", track.trackId());
        musicInfo.addProperty("name", track.title());
        musicInfo.addProperty("singer", track.artist());
        musicInfo.addProperty("albumName", track.album());
        musicInfo.addProperty("interval", formatDuration(track.durationSeconds()));
        musicInfo.addProperty("hash", track.hashForQuality("128k"));

        JsonObject types = new JsonObject();
        addHashType(types, "128k", track.hash128());
        addHashType(types, "320k", track.hash320());
        addHashType(types, "flac", track.hashFlac());
        addHashType(types, "flac24bit", track.hashHires());
        musicInfo.add("_types", types);

        JsonObject info = new JsonObject();
        info.addProperty("type", track.quality());
        info.add("musicInfo", musicInfo);

        JsonObject root = new JsonObject();
        root.addProperty("source", track.source());
        root.addProperty("action", "musicUrl");
        root.add("info", info);
        return root;
    }

    private static void addHashType(JsonObject types, String quality, String hash) {
        if (hash == null || hash.isBlank()) return;
        JsonObject value = new JsonObject();
        value.addProperty("hash", hash);
        types.add(quality, value);
    }

    private static String formatDuration(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static URI parseResponse(URI endpoint, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Playback API returned HTTP " + response.statusCode());
        }
        JsonElement json = JsonParser.parseString(response.body());
        String url = null;
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            url = json.getAsString();
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            url = stringMember(object, "url");
            if (url == null && object.has("data") && object.get("data").isJsonObject()) {
                url = stringMember(object.getAsJsonObject("data"), "url");
            }
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Playback API response does not contain a URL");
        }
        URI audioUri = URI.create(url);
        validateAudioUri(endpoint, audioUri);
        return audioUri;
    }

    private static String stringMember(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static void validateAudioUri(URI endpoint, URI audioUri) {
        validateHttpUri(audioUri, "audio");
        String audioHost = audioUri.getHost().toLowerCase(Locale.ROOT);
        String endpointHost = endpoint.getHost().toLowerCase(Locale.ROOT);
        if (hostMatches(audioHost, endpointHost)) {
            return;
        }
        for (String configured : MusicBoxConfig.ALLOWED_AUDIO_HOSTS.get()) {
            String allowed = configured.strip().toLowerCase(Locale.ROOT);
            if (!allowed.isEmpty() && hostMatches(audioHost, allowed)) {
                return;
            }
        }
        throw new IllegalStateException("Resolved audio host is not allowed: " + audioHost);
    }

    private static boolean hostMatches(String actual, String allowed) {
        return actual.equals(allowed) || actual.endsWith("." + allowed);
    }

    private static void validateHttpUri(URI uri, String purpose) {
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Invalid " + purpose + " URL");
        }
        boolean localhost = uri.getHost().equalsIgnoreCase("localhost")
                || uri.getHost().equals("127.0.0.1")
                || uri.getHost().equals("::1");
        if (MusicBoxConfig.REQUIRE_HTTPS.getAsBoolean() && !localhost && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("HTTPS is required for the " + purpose + " URL");
        }
    }

    private static Duration timeout() {
        return Duration.ofSeconds(MusicBoxConfig.HTTP_TIMEOUT_SECONDS.getAsInt());
    }
}
