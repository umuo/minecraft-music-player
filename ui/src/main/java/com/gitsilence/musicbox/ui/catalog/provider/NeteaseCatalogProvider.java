package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.CatalogException;
import com.gitsilence.musicbox.ui.catalog.CatalogPlaylist;
import com.gitsilence.musicbox.ui.catalog.CatalogTrack;
import com.gitsilence.musicbox.ui.catalog.MusicCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import com.gitsilence.musicbox.ui.catalog.PageResult;
import com.gitsilence.musicbox.ui.catalog.PlaylistDetail;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class NeteaseCatalogProvider implements MusicCatalogProvider {
    private static final Set<String> PLAYLIST_HOSTS = Set.of("music.163.com", "y.music.163.com");
    private static final String SEARCH_URL = "https://music.163.com/api/search/get/web";
    private static final String PLAYLIST_URL = "https://music.163.com/api/playlist/list";
    private static final String DETAIL_URL = "https://music.163.com/api/v6/playlist/detail";
    private final CatalogHttp http = new CatalogHttp();

    @Override
    public MusicPlatform platform() {
        return MusicPlatform.NETEASE;
    }

    @Override
    public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        return http.getJson(SEARCH_URL, CatalogHttp.params(
                        "s", query,
                        "type", 1,
                        "offset", (page - 1) * pageSize,
                        "total", true,
                        "limit", pageSize
                ))
                .thenApply(json -> {
                    JsonObject result = JsonSupport.object(root(json), "result");
                    List<CatalogTrack> tracks = parseTracks(JsonSupport.array(result, "songs"));
                    return new PageResult<>(tracks, page, pageSize, JsonSupport.number(result, "songCount"));
                });
    }

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        if (query == null || query.isBlank()) {
            return http.getJson(PLAYLIST_URL, CatalogHttp.params(
                            "cat", "全部",
                            "order", "hot",
                            "offset", (page - 1) * pageSize,
                            "total", true,
                            "limit", pageSize
                    ))
                    .thenApply(json -> {
                        JsonObject body = root(json);
                        return new PageResult<>(parsePlaylists(JsonSupport.array(body, "playlists")), page, pageSize,
                                JsonSupport.number(body, "total"));
                    });
        }
        return http.getJson(SEARCH_URL, CatalogHttp.params(
                        "s", query,
                        "type", 1000,
                        "offset", (page - 1) * pageSize,
                        "total", true,
                        "limit", pageSize
                ))
                .thenApply(json -> {
                    JsonObject result = JsonSupport.object(root(json), "result");
                    return new PageResult<>(parsePlaylists(JsonSupport.array(result, "playlists")), page, pageSize,
                            JsonSupport.number(result, "playlistCount"));
                });
    }

    private static final List<CatalogPlaylist> RANKINGS = List.of(
            new CatalogPlaylist(MusicPlatform.NETEASE, "19723756", "飙升榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "3778678", "热歌榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "3779629", "新歌榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "2884035", "原创榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "71384707", "古典榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "2250011882", "抖音榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "745956260", "韩语榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "1978921795", "电音榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "2006508653", "电竞榜", "网易云音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.NETEASE, "21845217", "KTV唛榜", "网易云音乐", "", 100, 0, "")
    );

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> rankings(int page, int pageSize) {
        return CompletableFuture.completedFuture(new PageResult<>(RANKINGS, 1, RANKINGS.size(), RANKINGS.size()));
    }

    @Override
    public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        return http.getJson(DETAIL_URL, CatalogHttp.params("id", playlist.id(), "n", 1000, "s", 8))
                .thenApply(NeteaseCatalogProvider::parseDetail);
    }

    static PlaylistDetail parseDetail(JsonElement json) {
                    JsonObject body = root(json);
                    if (JsonSupport.integer(body, "code") != 200) {
                        throw new CatalogException("NetEase rejected the playlist request");
                    }
                    JsonObject rawPlaylist = JsonSupport.object(body, "playlist");
                    CatalogPlaylist detail = parsePlaylist(rawPlaylist);
                    return new PlaylistDetail(detail, parseTracks(JsonSupport.array(rawPlaylist, "tracks")));
    }

    @Override public com.gitsilence.musicbox.ui.catalog.PlaylistImportRef normalizePlaylist(String input) {
        String id = PlaylistUrlSupport.numericIdOrUrl(input, PLAYLIST_HOSTS, "id", "playlistId");
        return new com.gitsilence.musicbox.ui.catalog.PlaylistImportRef(platform(), id,
                "https://music.163.com/#/playlist?id=" + id);
    }

    private static JsonObject root(JsonElement json) {
        if (!json.isJsonObject()) throw new CatalogException("Unexpected NetEase response");
        JsonObject root = json.getAsJsonObject();
        int code = JsonSupport.integer(root, "code");
        if (code != 0 && code != 200) throw new CatalogException("NetEase returned code " + code);
        return root;
    }

    static List<CatalogTrack> parseTracks(JsonArray rawTracks) {
        List<CatalogTrack> tracks = new ArrayList<>();
        for (JsonElement element : rawTracks) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonObject album = item.has("album") ? JsonSupport.object(item, "album") : JsonSupport.object(item, "al");
            JsonArray artists = item.has("artists") ? JsonSupport.array(item, "artists") : JsonSupport.array(item, "ar");
            long duration = JsonSupport.number(item, "duration");
            if (duration == 0) duration = JsonSupport.number(item, "dt");
            String cover = JsonSupport.string(album, "picUrl");
            tracks.add(new CatalogTrack(
                    MusicPlatform.NETEASE,
                    JsonSupport.string(item, "id"),
                    JsonSupport.string(item, "name"),
                    JsonSupport.joinedNames(artists, "name"),
                    JsonSupport.string(album, "name"),
                    (int) (duration / 1000),
                    cover,
                    "", "", "", ""
            ));
        }
        return tracks;
    }

    private static List<CatalogPlaylist> parsePlaylists(JsonArray rawPlaylists) {
        List<CatalogPlaylist> playlists = new ArrayList<>();
        for (JsonElement element : rawPlaylists) {
            if (element.isJsonObject()) playlists.add(parsePlaylist(element.getAsJsonObject()));
        }
        return playlists;
    }

    private static CatalogPlaylist parsePlaylist(JsonObject item) {
        JsonObject creator = JsonSupport.object(item, "creator");
        return new CatalogPlaylist(
                MusicPlatform.NETEASE,
                JsonSupport.string(item, "id"),
                JsonSupport.string(item, "name"),
                JsonSupport.string(creator, "nickname"),
                JsonSupport.string(item, "coverImgUrl"),
                JsonSupport.integer(item, "trackCount"),
                JsonSupport.number(item, "playCount"),
                JsonSupport.string(item, "description")
        );
    }
}
