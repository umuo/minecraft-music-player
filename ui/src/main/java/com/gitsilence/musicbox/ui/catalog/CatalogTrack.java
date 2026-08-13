package com.gitsilence.musicbox.ui.catalog;

public record CatalogTrack(
        MusicPlatform platform,
        String id,
        String title,
        String artist,
        String album,
        int durationSeconds,
        String coverUrl,
        String hash128,
        String hash320,
        String hashFlac,
        String hashHires
) {
    public CatalogTrack {
        id = safe(id);
        title = safe(title);
        artist = safe(artist);
        album = safe(album);
        coverUrl = secureUrl(coverUrl);
        hash128 = safe(hash128);
        hash320 = safe(hash320);
        hashFlac = safe(hashFlac);
        hashHires = safe(hashHires);
        durationSeconds = Math.max(0, durationSeconds);
    }

    public boolean hasQuality(String quality) {
        return switch (quality) {
            case "320k" -> !hash320.isEmpty() || platform == MusicPlatform.NETEASE;
            case "flac" -> !hashFlac.isEmpty() || platform == MusicPlatform.NETEASE;
            case "flac24bit" -> !hashHires.isEmpty() || platform == MusicPlatform.NETEASE;
            default -> !hash128.isEmpty() || platform == MusicPlatform.NETEASE;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String secureUrl(String value) {
        String url = safe(value).replace("{size}", "240");
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }
}
