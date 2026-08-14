package com.gitsilence.musicbox.server.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** JSON persistence containing public catalog metadata only. */
public final class SharedPlaylistStore {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;

    public SharedPlaylistStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized List<SharedPlaylist> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null || data.version != FORMAT_VERSION || data.playlists == null) return List.of();
            return List.copyOf(data.playlists);
        }
    }

    public synchronized void save(List<SharedPlaylist> playlists) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(new Data(FORMAT_VERSION, new ArrayList<>(playlists)), writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Data(int version, List<SharedPlaylist> playlists) {}
}
