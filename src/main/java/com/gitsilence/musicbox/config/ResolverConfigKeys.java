package com.gitsilence.musicbox.config;

import java.util.Set;

public final class ResolverConfigKeys {
    public static final String PLAYBACK_API_URL = "playbackApiUrl";
    public static final String PLAYBACK_API_TOKEN = "playbackApiToken";
    public static final String ALLOWED_AUDIO_HOSTS = "allowedAudioHosts";
    public static final String HTTP_TIMEOUT_SECONDS = "httpTimeoutSeconds";
    public static final String REQUIRE_HTTPS = "requireHttps";
    public static final String DEFAULT_QUALITY = "defaultQuality";
    public static final Set<String> SERVER = Set.of(PLAYBACK_API_URL, PLAYBACK_API_TOKEN, ALLOWED_AUDIO_HOSTS,
            HTTP_TIMEOUT_SECONDS, REQUIRE_HTTPS);
    public static final Set<String> CLIENT = Set.of(DEFAULT_QUALITY);
    private ResolverConfigKeys() { }
}
