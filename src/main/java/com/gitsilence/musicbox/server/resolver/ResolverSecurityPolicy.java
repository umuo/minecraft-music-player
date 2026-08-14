package com.gitsilence.musicbox.server.resolver;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class ResolverSecurityPolicy {
    private ResolverSecurityPolicy() { }

    public static URI validateEndpoint(String value, boolean requireHttps) {
        if (value == null || value.isBlank()) throw new IllegalStateException("The server playback API URL is not configured");
        URI uri = URI.create(value.strip());
        validateHttp(uri, "playback API", requireHttps);
        return uri;
    }

    public static URI validateAudio(String value, List<? extends String> allowedHosts, boolean requireHttps) {
        URI uri = URI.create(value);
        validateHttp(uri, "audio", requireHttps);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String entry : allowedHosts) {
            String allowed = entry.strip().toLowerCase(Locale.ROOT);
            if (!allowed.isEmpty() && (host.equals(allowed) || host.endsWith("." + allowed))) return uri;
        }
        throw new IllegalArgumentException("Resolved audio host is not allowed: " + host);
    }

    public static URI validateClientAudio(String value) {
        URI uri = URI.create(value);
        validateHttp(uri, "audio", false);
        return uri;
    }

    private static void validateHttp(URI uri, String purpose, boolean requireHttps) {
        String scheme = uri.getScheme();
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Invalid " + purpose + " URL");
        }
        boolean loopback = uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1")
                || uri.getHost().equals("::1");
        if (requireHttps && !loopback && !"https".equalsIgnoreCase(scheme))
            throw new IllegalArgumentException("HTTPS is required for the " + purpose + " URL");
    }
}
