package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Catalog adapter for QQ Music's public web search endpoints. Playback remains server-resolved. */
public final class QqCatalogProvider implements MusicCatalogProvider {
    private static final String TRACK_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String PLAYLIST_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist";
    private static final String PLAYLIST_DETAIL = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg";
    private static final Map<String, String> HEADERS = Map.of("Referer", "https://y.qq.com/");
    private final CatalogHttp http = new CatalogHttp();

    @Override public MusicPlatform platform() { return MusicPlatform.QQ; }

    @Override public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        return http.getJson(TRACK_SEARCH, CatalogHttp.params("w", query, "format", "json", "p", page, "n", pageSize), HEADERS)
                .thenApply(json -> {
                    JsonObject song = JsonSupport.object(JsonSupport.object(root(json), "data"), "song");
                    return new PageResult<>(parseTracks(JsonSupport.array(song, "list")), page, pageSize,
                            JsonSupport.number(song, "totalnum"));
                });
    }

    @Override public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        String keyword = query == null || query.isBlank() ? "流行" : query;
        return http.getJson(PLAYLIST_SEARCH, CatalogHttp.params("remoteplace", "txt.yqq.playlist", "query", keyword,
                        "page_no", page - 1, "num_per_page", pageSize, "format", "json"), HEADERS)
                .thenApply(json -> {
                    JsonObject data = JsonSupport.object(root(json), "data");
                    return new PageResult<>(parsePlaylists(JsonSupport.array(data, "list")), page, pageSize,
                            JsonSupport.number(data, "display_num"));
                });
    }

    @Override public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        return http.getJson(PLAYLIST_DETAIL, CatalogHttp.params("type", 1, "json", 1, "utf8", 1, "onlysong", 0,
                        "disstid", playlist.id(), "format", "json"), HEADERS)
                .thenApply(json -> {
                    JsonArray lists = JsonSupport.array(root(json), "cdlist");
                    if (lists.isEmpty() || !lists.get(0).isJsonObject()) throw new CatalogException("QQ Music playlist was unavailable");
                    JsonObject item = lists.get(0).getAsJsonObject();
                    CatalogPlaylist detail = new CatalogPlaylist(MusicPlatform.QQ, JsonSupport.string(item, "disstid"),
                            JsonSupport.string(item, "dissname"), JsonSupport.string(item, "nickname"),
                            JsonSupport.string(item, "logo"), JsonSupport.integer(item, "songnum"),
                            JsonSupport.number(item, "visitnum"), JsonSupport.string(item, "desc"));
                    return new PlaylistDetail(detail, parseTracks(JsonSupport.array(item, "songlist")));
                });
    }

    static List<CatalogTrack> parseTracks(JsonArray values) {
        List<CatalogTrack> out = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) continue;
            JsonObject item = value.getAsJsonObject();
            JsonObject album = JsonSupport.object(item, "album");
            String mid = first(item, "songmid", "mid");
            String albumMid = first(item, "albummid");
            if (albumMid.isEmpty()) albumMid = JsonSupport.string(album, "mid");
            String cover = albumMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
            JsonArray singers = item.has("singer") ? JsonSupport.array(item, "singer") : JsonSupport.array(item, "singers");
            int duration = JsonSupport.integer(item, "interval");
            if (duration == 0) duration = JsonSupport.integer(item, "duration");
            JsonObject file = JsonSupport.object(item, "file");
            long size128 = number(item, file, "size128", "size_128mp3");
            long size320 = number(item, file, "size320", "size_320mp3");
            long sizeFlac = number(item, file, "sizeflac", "size_flac");
            String albumName = first(item, "albumname");
            if (albumName.isEmpty()) albumName = JsonSupport.string(album, "name");
            out.add(new CatalogTrack(MusicPlatform.QQ, mid, first(item, "songname", "name"),
                    JsonSupport.joinedNames(singers, "name"), albumName, duration, cover,
                    size128 > 0 ? mid : "", size320 > 0 ? mid : "", sizeFlac > 0 ? mid : "", ""));
        }
        return out;
    }

    static List<CatalogPlaylist> parsePlaylists(JsonArray values) {
        List<CatalogPlaylist> out = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            out.add(new CatalogPlaylist(MusicPlatform.QQ, JsonSupport.string(item, "dissid"),
                    JsonSupport.string(item, "dissname"), JsonSupport.string(JsonSupport.object(item, "creator"), "name"),
                    JsonSupport.string(item, "imgurl"), JsonSupport.integer(item, "song_count"),
                    JsonSupport.number(item, "listennum"), JsonSupport.htmlDecode(JsonSupport.string(item, "introduction"))));
        }
        return out;
    }

    private static JsonObject root(JsonElement json) {
        if (!json.isJsonObject()) throw new CatalogException("Unexpected QQ Music response");
        JsonObject root = json.getAsJsonObject();
        if (JsonSupport.integer(root, "code") != 0) throw new CatalogException("QQ Music rejected the request");
        return root;
    }
    private static String first(JsonObject object, String... names) {
        for (String name : names) { String value = JsonSupport.string(object, name); if (!value.isBlank()) return value; }
        return "";
    }
    private static long number(JsonObject direct, JsonObject nested, String... names) {
        for (String name : names) {
            long value = JsonSupport.number(direct, name);
            if (value == 0) value = JsonSupport.number(nested, name);
            if (value > 0) return value;
        }
        return 0;
    }
}
