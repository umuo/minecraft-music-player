package com.gitsilence.musicbox.ui.catalog;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CachingCatalogProvider implements MusicCatalogProvider {
    private static final long CACHE_EXPIRATION_MS = 5 * 60 * 1000L; // 5 minutes
    
    private record CacheEntry(CompletableFuture<?> future, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }

    private final MusicCatalogProvider delegate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachingCatalogProvider(MusicCatalogProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public MusicPlatform platform() {
        return delegate.platform();
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> getCached(String key, java.util.function.Supplier<CompletableFuture<T>> supplier) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (CompletableFuture<T>) entry.future();
        }
        CompletableFuture<T> future = supplier.get();
        cache.put(key, new CacheEntry(future, System.currentTimeMillis()));
        return future;
    }

    @Override
    public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        String key = "tracks:" + query + ":" + page + ":" + pageSize;
        return getCached(key, () -> delegate.searchTracks(query, page, pageSize));
    }

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        String key = "playlists:" + query + ":" + page + ":" + pageSize;
        return getCached(key, () -> delegate.playlists(query, page, pageSize));
    }

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> hotPlaylists(int page, int pageSize) {
        String key = "hotPlaylists:" + page + ":" + pageSize;
        return getCached(key, () -> delegate.hotPlaylists(page, pageSize));
    }

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> rankings(int page, int pageSize) {
        String key = "rankings:" + page + ":" + pageSize;
        return getCached(key, () -> delegate.rankings(page, pageSize));
    }

    @Override
    public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        String key = "detail:" + playlist.id();
        return getCached(key, () -> delegate.playlistDetail(playlist));
    }

    @Override
    public PlaylistImportRef normalizePlaylist(String urlOrId) {
        return delegate.normalizePlaylist(urlOrId);
    }
}
