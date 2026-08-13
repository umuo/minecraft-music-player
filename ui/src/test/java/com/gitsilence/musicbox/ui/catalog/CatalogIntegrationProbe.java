package com.gitsilence.musicbox.ui.catalog;

import com.gitsilence.musicbox.ui.state.BrowserSessionState;
import java.util.concurrent.TimeUnit;

public final class CatalogIntegrationProbe {
    private CatalogIntegrationProbe() {
    }

    public static void main(String[] args) throws Exception {
        probeSessionState();
        for (MusicPlatform platform : MusicPlatform.values()) {
            MusicCatalogProvider provider = MusicCatalog.provider(platform);
            PageResult<CatalogTrack> tracks = provider.searchTracks("周杰伦", 1, 3).get(30, TimeUnit.SECONDS);
            require(!tracks.items().isEmpty(), platform + " track search returned no results");

            PageResult<CatalogPlaylist> featured = provider.playlists("", 1, 3).get(30, TimeUnit.SECONDS);
            require(!featured.items().isEmpty(), platform + " featured playlists returned no results");

            PageResult<CatalogPlaylist> searched = provider.playlists("摇滚", 1, 3).get(30, TimeUnit.SECONDS);
            require(!searched.items().isEmpty(), platform + " playlist search returned no results");

            PlaylistDetail detail = provider.playlistDetail(featured.items().getFirst()).get(30, TimeUnit.SECONDS);
            require(!detail.tracks().isEmpty(), platform + " playlist detail returned no tracks");

            System.out.printf("%s: tracks=%d, featured=%d, playlistSearch=%d, detailTracks=%d%n",
                    platform.source(), tracks.items().size(), featured.items().size(), searched.items().size(), detail.tracks().size());
        }
    }

    private static void probeSessionState() {
        BrowserSessionState state = new BrowserSessionState();
        state.remember(MusicPlatform.KUGOU, false, "周杰伦");
        state.remember(MusicPlatform.KUGOU, false, "林俊杰");
        state.remember(MusicPlatform.KUGOU, false, "周杰伦");
        require(state.history(MusicPlatform.KUGOU, false).equals(java.util.List.of("周杰伦", "林俊杰")),
                "recent searches should be unique and newest-first");
        require(state.history(MusicPlatform.NETEASE, false).isEmpty(),
                "recent searches must be isolated by platform");
        require(state.history(MusicPlatform.KUGOU, true).isEmpty(),
                "track and playlist searches must use separate histories");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
