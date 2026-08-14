package com.gitsilence.musicbox.server.resolver;

import com.gitsilence.musicbox.ui.catalog.ResolverSourceInfo;
import java.util.List;

/** Complete server-only resolver configuration. Never put this type in a network payload. */
public record ResolverSourceConfig(
        String id, String displayName, String playbackApiUrl, String token,
        List<String> allowedAudioHosts, boolean requireHttps, int timeoutSeconds,
        List<String> platforms, List<String> qualities, List<String> capabilities,
        boolean enabled, int permissionLevel
) {
    public ResolverSourceConfig {
        allowedAudioHosts = List.copyOf(allowedAudioHosts);
        platforms = List.copyOf(platforms);
        qualities = List.copyOf(qualities);
        capabilities = List.copyOf(capabilities);
    }

    public ResolverSourceInfo publicInfo() {
        return new ResolverSourceInfo(id, displayName, platforms, qualities, capabilities);
    }
}
