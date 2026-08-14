package com.gitsilence.musicbox.server.resolver;

import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

public final class ResolverSourceRegistry {
    public static final String DEFAULT_ID = "default";
    private static final Set<String> PLATFORMS = Set.of("kw", "kg", "tx", "wy", "mg", "local");
    private static final Set<String> QUALITIES = Set.of("128k", "320k", "flac", "flac24bit");
    private static final Set<String> CAPABILITIES = Set.of("musicUrl", "lyric");
    private static final Gson GSON = new Gson();

    private ResolverSourceRegistry() { }

    public static Map<String, ResolverSourceConfig> configuredSources() {
        ResolverSourceConfig legacy = null;
        String legacyUrl = MusicBoxConfig.PLAYBACK_API_URL.get().strip();
        if (!legacyUrl.isEmpty()) {
            legacy = new ResolverSourceConfig(DEFAULT_ID, "Default", legacyUrl,
                    MusicBoxConfig.PLAYBACK_API_TOKEN.get(), List.copyOf(MusicBoxConfig.ALLOWED_AUDIO_HOSTS.get()),
                    MusicBoxConfig.REQUIRE_HTTPS.get(), MusicBoxConfig.HTTP_TIMEOUT_SECONDS.getAsInt(),
                    List.copyOf(PLATFORMS), List.copyOf(QUALITIES), List.of("musicUrl", "lyric"), true, 0);
        }
        return parse(MusicBoxConfig.RESOLVER_SOURCES.get(), legacy);
    }

    public static Map<String, ResolverSourceConfig> parse(List<? extends String> definitions,
                                                           ResolverSourceConfig legacy) {
        Map<String, ResolverSourceConfig> result = new LinkedHashMap<>();
        if (legacy != null) result.put(DEFAULT_ID, legacy);
        for (String definition : definitions) {
            ResolverSourceConfig source = parseOne(definition);
            if (result.putIfAbsent(source.id(), source) != null)
                throw new IllegalArgumentException("Duplicate resolver source id: " + source.id());
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static ResolverSourceConfig parseOne(String definition) {
        try {
            JsonObject object = GSON.fromJson(definition, JsonObject.class);
            if (object == null) throw new IllegalArgumentException("Resolver source must be a JSON object");
            String id = required(object, "id");
            if (!id.matches("[a-z0-9][a-z0-9_-]{0,31}"))
                throw new IllegalArgumentException("Invalid resolver source id: " + id);
            String displayName = optional(object, "displayName", id);
            if (displayName.length() > 64) throw new IllegalArgumentException("Resolver displayName is too long: " + id);
            String endpoint = required(object, "playbackApiUrl");
            boolean requireHttps = bool(object, "requireHttps", true);
            ResolverSecurityPolicy.validateEndpoint(endpoint, requireHttps);
            int timeout = integer(object, "timeoutSeconds", 15, 3, 60);
            int permission = integer(object, "permissionLevel", 0, 0, 4);
            List<String> hosts = list(object, "allowedAudioHosts", Set.of());
            List<String> platforms = list(object, "platforms", PLATFORMS);
            List<String> qualities = list(object, "qualities", QUALITIES);
            List<String> capabilities = list(object, "capabilities", CAPABILITIES);
            validateSubset(id, "platforms", platforms, PLATFORMS);
            validateSubset(id, "qualities", qualities, QUALITIES);
            validateSubset(id, "capabilities", capabilities, CAPABILITIES);
            if (!capabilities.contains("musicUrl"))
                throw new IllegalArgumentException("Resolver source " + id + " must support musicUrl");
            return new ResolverSourceConfig(id, displayName, endpoint, optional(object, "token", ""), hosts,
                    requireHttps, timeout, platforms, qualities, capabilities,
                    bool(object, "enabled", true), permission);
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("Invalid resolver source JSON", error);
        }
    }

    private static String required(JsonObject o, String key) {
        String value = optional(o, key, "").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Resolver source requires " + key);
        return value;
    }
    private static String optional(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        int value = o.has(key) ? o.get(key).getAsInt() : fallback;
        if (value < min || value > max) throw new IllegalArgumentException(key + " must be " + min + ".." + max);
        return value;
    }
    private static List<String> list(JsonObject o, String key, Set<String> fallback) {
        if (!o.has(key)) return List.copyOf(fallback);
        List<String> result = new ArrayList<>();
        o.getAsJsonArray(key).forEach(value -> result.add(value.getAsString().strip()));
        return List.copyOf(result);
    }
    private static void validateSubset(String id, String field, List<String> values, Set<String> allowed) {
        if (values.isEmpty() || !allowed.containsAll(values))
            throw new IllegalArgumentException("Invalid " + field + " for resolver source " + id);
    }
}
