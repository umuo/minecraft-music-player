package com.gitsilence.musicbox.server.playlist;

import com.gitsilence.musicbox.ui.catalog.CatalogTrack;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;

public record SharedTrack(String id, String title, String artist, String album, int durationSeconds, String coverUrl,
                          String hash128, String hash320, String hashFlac, String hashHires) {
    public SharedTrack {
        id = safe(id, 256); title = safe(title, 128); artist = safe(artist, 128); album = safe(album, 128);
        coverUrl = safePublicUrl(coverUrl); hash128 = safe(hash128, 128); hash320 = safe(hash320, 128);
        hashFlac = safe(hashFlac, 128); hashHires = safe(hashHires, 128);
        durationSeconds = Math.max(0, Math.min(86_400, durationSeconds));
    }
    public static SharedTrack from(CatalogTrack track) {
        return new SharedTrack(track.id(), track.title(), track.artist(), track.album(), track.durationSeconds(),
                track.coverUrl(), track.hash128(), track.hash320(), track.hashFlac(), track.hashHires());
    }
    public CatalogTrack toCatalogTrack(MusicPlatform platform) {
        return new CatalogTrack(platform, id, title, artist, album, durationSeconds, coverUrl,
                hash128, hash320, hashFlac, hashHires);
    }
    private static String safe(String value, int max) { String text=value==null?"":value.strip(); return text.length()<=max?text:text.substring(0,max); }
    private static String safePublicUrl(String value) { String url=safe(value,512); return url.startsWith("https://")?url:""; }
}
