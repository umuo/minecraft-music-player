package com.gitsilence.musicbox.ui.catalog;

public record CatalogPlaylist(
        MusicPlatform platform,
        String id,
        String name,
        String creator,
        String coverUrl,
        int trackCount,
        long playCount,
        String description
) {
    public CatalogPlaylist {
        id = safe(id);
        name = safe(name);
        creator = safe(creator);
        coverUrl = secureUrl(coverUrl);
        description = safe(description);
        trackCount = Math.max(0, trackCount);
        playCount = Math.max(0, playCount);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String secureUrl(String value) {
        String url = safe(value).replace("{size}", "240");
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }
}
