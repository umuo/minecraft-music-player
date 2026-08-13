package com.gitsilence.musicbox.ui.state;

import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Keeps convenient browser state in memory without writing searches to disk or the server. */
public final class BrowserSessionState {
    private static final int HISTORY_LIMIT = 8;

    private final Map<MusicPlatform, ModeState> tracks = new EnumMap<>(MusicPlatform.class);
    private final Map<MusicPlatform, ModeState> playlists = new EnumMap<>(MusicPlatform.class);
    private MusicPlatform platform = MusicPlatform.KUGOU;
    private boolean playlistMode;
    private String quality;

    public BrowserSessionState() {
        for (MusicPlatform value : MusicPlatform.values()) {
            tracks.put(value, new ModeState());
            playlists.put(value, new ModeState());
        }
    }

    public MusicPlatform platform() { return platform; }

    public void platform(MusicPlatform platform) { this.platform = platform; }

    public boolean playlistMode() { return playlistMode; }

    public void playlistMode(boolean playlistMode) { this.playlistMode = playlistMode; }

    public String qualityOr(String fallback) {
        if (quality == null) quality = fallback;
        return quality;
    }

    public void setQuality(String quality) { this.quality = quality; }

    public String query(MusicPlatform platform, boolean playlistMode) {
        return state(platform, playlistMode).query;
    }

    public void query(MusicPlatform platform, boolean playlistMode, String query) {
        state(platform, playlistMode).query = query;
    }

    public void remember(MusicPlatform platform, boolean playlistMode, String query) {
        String normalized = query.strip();
        ModeState state = state(platform, playlistMode);
        state.query = query;
        if (normalized.isEmpty()) return;
        state.history.remove(normalized);
        state.history.addFirst(normalized);
        while (state.history.size() > HISTORY_LIMIT) state.history.removeLast();
    }

    public List<String> history(MusicPlatform platform, boolean playlistMode) {
        return new ArrayList<>(state(platform, playlistMode).history);
    }

    private ModeState state(MusicPlatform platform, boolean playlistMode) {
        return (playlistMode ? playlists : tracks).get(platform);
    }

    private static final class ModeState {
        private String query = "";
        private final ArrayDeque<String> history = new ArrayDeque<>();
    }
}
