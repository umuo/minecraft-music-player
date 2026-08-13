package com.gitsilence.musicbox.ui.catalog;

import java.util.concurrent.CompletableFuture;

public interface MusicCatalogProvider {
    MusicPlatform platform();

    CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize);

    CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize);

    CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist);
}
