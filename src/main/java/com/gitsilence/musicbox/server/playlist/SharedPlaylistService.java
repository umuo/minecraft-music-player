package com.gitsilence.musicbox.server.playlist;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.ui.catalog.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class SharedPlaylistService {
    public static final int REQUIRED_PERMISSION = 2;
    public static final int MAX_PLAYLISTS = 64;
    public static final int MAX_TRACKS = 500;
    public static final int MAX_DISPLAY_NAME = 128;
    public static final int MAX_INPUT = 512;
    public static final int MAX_PUBLIC_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(20);
    private static final Map<MinecraftServer, SharedPlaylistService> INSTANCES = new WeakHashMap<>();

    private final SharedPlaylistStore store;
    private final Map<String, SharedPlaylist> playlists = new LinkedHashMap<>();
    private final Set<String> importing = ConcurrentHashMap.newKeySet();

    public SharedPlaylistService(SharedPlaylistStore store) {
        this.store = store;
        try { for (SharedPlaylist playlist : store.load()) playlists.put(playlist.key(), playlist); }
        catch (IOException error) { MusicBoxMod.LOGGER.error("Could not load shared playlists", error); }
    }

    public static synchronized SharedPlaylistService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, value -> {
            Path path = value.getWorldPath(LevelResource.ROOT).resolve("serverconfig/musicbox/shared_playlists.json");
            return new SharedPlaylistService(new SharedPlaylistStore(path));
        });
    }

    public static boolean canManage(int permissionLevel) { return permissionLevel >= REQUIRED_PERMISSION; }

    public synchronized List<SharedPlaylist> snapshot() { return List.copyOf(playlists.values()); }

    public CompletableFuture<SharedPlaylist> importPlaylist(String rawPlatform, String input, String displayName) {
        if (input == null || input.length() > MAX_INPUT || displayName != null && displayName.length() > MAX_DISPLAY_NAME)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Import input is too long"));
        final MusicPlatform platform;
        final PlaylistImportRef ref;
        try {
            platform = MusicPlatform.fromSource(rawPlatform);
            ref = MusicCatalog.provider(platform).normalizePlaylist(input);
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        String key = platform.source() + ':' + ref.playlistId();
        synchronized (this) {
            if (!playlists.containsKey(key) && playlists.size() >= MAX_PLAYLISTS)
                return CompletableFuture.failedFuture(new IllegalStateException("Shared playlist limit reached"));
        }
        if (!importing.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("Playlist import already in progress"));
        return MusicCatalog.provider(platform).playlistDetail(ref).orTimeout(IMPORT_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .thenApply(detail -> validated(key, ref, displayName, detail))
                .thenApply(playlist -> { synchronized (this) {
                    if (!playlists.containsKey(key) && playlists.size() >= MAX_PLAYLISTS)
                        throw new IllegalStateException("Shared playlist limit reached");
                    List<SharedPlaylist> candidate = new ArrayList<>(playlists.values());
                    candidate.removeIf(value -> value.key().equals(key)); candidate.add(playlist);
                    if (estimatedBytes(candidate) > MAX_PUBLIC_PAYLOAD_BYTES)
                        throw new CatalogException("Shared playlist payload limit reached");
                    playlists.put(key, playlist); persist();
                } return playlist; })
                .whenComplete((result, error) -> importing.remove(key));
    }

    public synchronized boolean delete(String key) {
        if (key == null || key.length() > 160 || playlists.remove(key) == null) return false;
        persist();
        return true;
    }

    private SharedPlaylist validated(String key, PlaylistImportRef ref, String displayName, PlaylistDetail detail) {
        if (detail == null || detail.playlist() == null || detail.tracks().isEmpty())
            throw new CatalogException("Platform returned an empty playlist");
        if (detail.tracks().size() > MAX_TRACKS) throw new CatalogException("Playlist has too many tracks");
        List<SharedTrack> tracks = detail.tracks().stream().filter(track -> !track.id().isBlank() && !track.title().isBlank())
                .map(SharedTrack::from).toList();
        if (tracks.isEmpty()) throw new CatalogException("Platform returned no usable tracks");
        CatalogPlaylist meta = detail.playlist();
        String name = displayName == null || displayName.isBlank() ? meta.name() : displayName.strip();
        if (name.isBlank()) name = ref.playlistId();
        SharedPlaylist result = new SharedPlaylist(key, ref.platform().source(), ref.playlistId(), ref.canonicalUrl(),
                name, meta.creator(), meta.coverUrl(), System.currentTimeMillis(), tracks);
        if (estimatedBytes(List.of(result)) > MAX_PUBLIC_PAYLOAD_BYTES) throw new CatalogException("Playlist response is too large");
        return result;
    }

    public static int estimatedBytes(List<SharedPlaylist> values) {
        int total = 32;
        for (SharedPlaylist playlist : values) {
            total += length(playlist.key()) + length(playlist.platform()) + length(playlist.playlistId())
                    + length(playlist.canonicalUrl()) + length(playlist.name()) + length(playlist.creator())
                    + length(playlist.coverUrl()) + 32;
            for (SharedTrack track : playlist.tracks()) total += length(track.id()) + length(track.title())
                    + length(track.artist()) + length(track.album()) + length(track.coverUrl()) + length(track.hash128())
                    + length(track.hash320()) + length(track.hashFlac()) + length(track.hashHires()) + 32;
        }
        return total;
    }
    private static int length(String value) { return value == null ? 0 : value.length() * 3; }
    private void persist() {
        try { store.save(List.copyOf(playlists.values())); }
        catch (IOException error) { throw new IllegalStateException("Could not save shared playlists", error); }
    }
}
