package com.gitsilence.musicbox.ui.catalog;

import java.util.List;

public record PlaylistDetail(CatalogPlaylist playlist, List<CatalogTrack> tracks) {
    public PlaylistDetail {
        tracks = List.copyOf(tracks);
    }
}
