package com.gitsilence.musicbox.playback;

import java.util.Set;

public record TrackRef(
        String source,
        String trackId,
        String title,
        String artist,
        String album,
        int durationSeconds,
        String hash128,
        String hash320,
        String hashFlac,
        String hashHires,
        String quality
) {
    public static final TrackRef EMPTY = new TrackRef("", "", "", "", "", 0, "", "", "", "", "128k");
    private static final Set<String> SOURCES = Set.of("kw", "kg", "tx", "wy", "mg", "local");
    private static final Set<String> QUALITIES = Set.of("128k", "320k", "flac", "flac24bit");

    public TrackRef {
        source = trim(source, 16);
        trackId = trim(trackId, 256);
        title = trim(title, 128);
        artist = trim(artist, 128);
        album = trim(album, 128);
        hash128 = trim(hash128, 128);
        hash320 = trim(hash320, 128);
        hashFlac = trim(hashFlac, 128);
        hashHires = trim(hashHires, 128);
        quality = trim(quality, 16);
        durationSeconds = Math.max(0, Math.min(durationSeconds, 86_400));
    }

    public boolean isValid() {
        return SOURCES.contains(source)
                && !trackId.isBlank()
                && !title.isBlank()
                && QUALITIES.contains(quality);
    }

    public String hashForQuality(String requestedQuality) {
        return switch (requestedQuality) {
            case "flac24bit" -> first(hashHires, hashFlac, hash320, hash128);
            case "flac" -> first(hashFlac, hash320, hash128);
            case "320k" -> first(hash320, hash128);
            default -> hash128;
        };
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }
}
