package com.gitsilence.musicbox.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MusicBoxConfig {
    public static final ModConfigSpec SERVER_SPEC;
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
        common.pop();
        common.push("resolver");
        PLAYBACK_API_URL = common.comment("Server-side LX-compatible resolver endpoint.")
                .define(ResolverConfigKeys.PLAYBACK_API_URL, "");
        PLAYBACK_API_TOKEN = common.comment("Optional bearer token. Never synchronized to clients.")
                .define(ResolverConfigKeys.PLAYBACK_API_TOKEN, "");
        ALLOWED_AUDIO_HOSTS = common.comment("Allowed final audio CDN hosts; exact hosts and subdomains match.")
                .defineListAllowEmpty(ResolverConfigKeys.ALLOWED_AUDIO_HOSTS, List.of(), () -> "", value -> value instanceof String);
        HTTP_TIMEOUT_SECONDS = common.comment("Resolver HTTP timeout in seconds.")
                .defineInRange(ResolverConfigKeys.HTTP_TIMEOUT_SECONDS, 15, 3, 60);
        REQUIRE_HTTPS = common.comment("Require HTTPS for resolver and audio URLs (localhost excepted).")
                .define(ResolverConfigKeys.REQUIRE_HTTPS, true);
        common.pop();
        SERVER_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("ui");
        DEFAULT_QUALITY = client.comment("Preferred LX quality: 128k, 320k, flac, or flac24bit.")
                .define(ResolverConfigKeys.DEFAULT_QUALITY, "320k", MusicBoxConfig::validQuality);
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
