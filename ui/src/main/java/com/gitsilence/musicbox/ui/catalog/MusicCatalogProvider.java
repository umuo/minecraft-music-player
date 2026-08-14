package com.gitsilence.musicbox.ui.catalog;

import java.util.concurrent.CompletableFuture;

public interface MusicCatalogProvider {
    MusicPlatform platform();

    CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize);

    CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize);

    CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist);

    /** Parses only an allow-listed public URL or a platform playlist id. No network request is made. */
    PlaylistImportRef normalizePlaylist(String urlOrId);

    default CompletableFuture<PlaylistDetail> playlistDetail(PlaylistImportRef playlist) {
        return playlistDetail(playlist.asCatalogPlaylist());
    }
}
