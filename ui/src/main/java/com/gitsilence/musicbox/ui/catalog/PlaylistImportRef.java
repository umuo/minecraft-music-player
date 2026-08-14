package com.gitsilence.musicbox.ui.catalog;

/** A validated public playlist identity. The URL never contains credentials or resolver configuration. */
public record PlaylistImportRef(MusicPlatform platform, String playlistId, String canonicalUrl) {
    public PlaylistImportRef {
        if (platform == null || playlistId == null || playlistId.isBlank()
                || canonicalUrl == null || canonicalUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid playlist identity");
        }
    }

    public CatalogPlaylist asCatalogPlaylist() {
        return new CatalogPlaylist(platform, playlistId, "", "", "", 0, 0, "");
    }
}
