package com.gitsilence.musicbox.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MusicBoxConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.IntValue BROADCAST_RADIUS;
    public static final ModConfigSpec.IntValue START_DELAY_TICKS;
    public static final ModConfigSpec.IntValue MAX_QUEUE_SIZE;
    public static final ModConfigSpec.BooleanValue REQUIRE_HTTPS;

    public static final ModConfigSpec.ConfigValue<String> PLAYBACK_API_URL;
    public static final ModConfigSpec.ConfigValue<String> PLAYBACK_API_TOKEN;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_QUALITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_AUDIO_HOSTS;
    public static final ModConfigSpec.IntValue HTTP_TIMEOUT_SECONDS;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        common.push("playback");
        BROADCAST_RADIUS = common.comment("Maximum distance in blocks at which players receive playback events.")
                .defineInRange("broadcastRadius", 64, 8, 256);
        START_DELAY_TICKS = common.comment("Delay before playback starts, allowing clients time to resolve and buffer the stream.")
                .defineInRange("startDelayTicks", 60, 20, 200);
        MAX_QUEUE_SIZE = common.comment("Maximum number of waiting tracks stored by each music box.")
                .defineInRange("maxQueueSize", 100, 1, 500);
        REQUIRE_HTTPS = common.comment("Reject non-HTTPS playback URLs, except localhost URLs used for development.")
                .define("requireHttps", true);
        common.pop();
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("resolver");
        PLAYBACK_API_URL = client.comment(
                        "HTTP JSON endpoint that resolves a LX-style musicUrl request.",
                        "Request: {source, action:'musicUrl', info:{type, musicInfo}}",
                        "Response: {url:'https://...'} or {data:{url:'https://...'}}")
                .define("playbackApiUrl", "");
        PLAYBACK_API_TOKEN = client.comment("Optional bearer token. Stored as plain text in the local config file.")
                .define("playbackApiToken", "");
        DEFAULT_QUALITY = client.comment("Preferred LX quality: 128k, 320k, flac, or flac24bit.")
                .define("defaultQuality", "320k", MusicBoxConfig::validQuality);
        ALLOWED_AUDIO_HOSTS = client.comment(
                        "Allowed hosts for resolved audio URLs. Exact hosts and subdomains are accepted.",
                        "The playback API host is always accepted. Keep this list narrow.")
                .defineListAllowEmpty("allowedAudioHosts", List.of(), () -> "", value -> value instanceof String);
        HTTP_TIMEOUT_SECONDS = client.comment("Timeout for resolver and audio HTTP requests.")
                .defineInRange("httpTimeoutSeconds", 15, 3, 60);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private MusicBoxConfig() {
    }

    private static boolean validQuality(Object value) {
        return value instanceof String text
                && (text.equals("128k") || text.equals("320k") || text.equals("flac") || text.equals("flac24bit"));
    }
}
