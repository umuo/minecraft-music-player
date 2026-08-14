package com.gitsilence.musicbox.ui.catalog;

import java.util.List;

/** The intentionally small, secret-free resolver view synchronized to clients. */
public record ResolverSourceInfo(String id, String displayName, List<String> platforms,
                                 List<String> qualities, List<String> capabilities) {
    public ResolverSourceInfo {
        platforms = List.copyOf(platforms);
        qualities = List.copyOf(qualities);
        capabilities = List.copyOf(capabilities);
    }

    public boolean supports(String platform, String quality) {
        return platforms.contains(platform) && qualities.contains(quality) && capabilities.contains("musicUrl");
    }
}
