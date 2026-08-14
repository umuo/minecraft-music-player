package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Adapter for Kuwo's public search and playlist catalog services. */
public final class KuwoCatalogProvider implements MusicCatalogProvider {
    private static final Set<String> PLAYLIST_HOSTS = Set.of("kuwo.cn", "www.kuwo.cn", "m.kuwo.cn", "h5app.kuwo.cn");
    private static final java.util.regex.Pattern PLAYLIST_PATH = java.util.regex.Pattern.compile("/(?:playlist_detail|playlist)/(\\d+)");
    private static final String SEARCH = "https://search.kuwo.cn/r.s";
    private static final String DETAIL = "https://nplserver.kuwo.cn/pl.svc";
    private final CatalogHttp http = new CatalogHttp();

    @Override public MusicPlatform platform() { return MusicPlatform.KUWO; }
    @Override public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        return search(query, page, pageSize, "music").thenApply(json -> new PageResult<>(
                parseTracks(JsonSupport.array(root(json), "abslist")), page, pageSize, JsonSupport.number(root(json), "TOTAL")));
    }
    @Override public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        String keyword = query == null || query.isBlank() ? "流行" : query;
        return search(keyword, page, pageSize, "playlist").thenApply(json -> new PageResult<>(
                parsePlaylists(JsonSupport.array(root(json), "abslist")), page, pageSize, JsonSupport.number(root(json), "TOTAL")));
    }
    private static final List<CatalogPlaylist> RANKINGS = List.of(
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_93", "飙升榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_16", "热歌榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_145", "会员榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_158", "抖音榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_187", "趋势榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_26", "怀旧榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_104", "华语榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_182", "粤语榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_22", "欧美榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_184", "韩语榜", "酷我音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUWO, "RANKING_183", "日语榜", "酷我音乐", "", 100, 0, "")
    );

    @Override public CompletableFuture<PageResult<CatalogPlaylist>> rankings(int page, int pageSize) {
        return CompletableFuture.completedFuture(new PageResult<>(RANKINGS, 1, RANKINGS.size(), RANKINGS.size()));
    }

    @Override public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        if (playlist.id().startsWith("RANKING_")) {
            String bangid = playlist.id().substring(8);
            return http.getJson("http://kbangserver.kuwo.cn/ksong.s", CatalogHttp.params("from", "pc", "fmt", "json", "pn", 0, "rn", 100, "type", "bang", "data", "content", "id", bangid, "show_copyright_off", 0, "pcmp4", 1, "isbang", 1))
                    .thenApply(json -> new PlaylistDetail(playlist, parseTracks(JsonSupport.array(root(json), "musiclist"))));
        }
        return http.getJson(DETAIL, CatalogHttp.params("op", "getlistinfo", "pid", playlist.id(), "pn", 0, "rn", 1000,
                        "encode", "utf8", "keyset", "pl2012", "identity", "kuwo", "pcmp4", 1))
                .thenApply(KuwoCatalogProvider::parseDetail);
    }
    static PlaylistDetail parseDetail(JsonElement json) {
                    JsonObject body = root(json);
                    CatalogPlaylist detail = new CatalogPlaylist(MusicPlatform.KUWO, JsonSupport.string(body, "id"),
                            JsonSupport.string(body, "title"), JsonSupport.string(body, "uname"), JsonSupport.string(body, "pic"),
                            JsonSupport.integer(body, "total"), JsonSupport.number(body, "playnum"), JsonSupport.string(body, "info"));
                    return new PlaylistDetail(detail, parseTracks(JsonSupport.array(body, "musiclist")));
    }
    @Override public com.gitsilence.musicbox.ui.catalog.PlaylistImportRef normalizePlaylist(String input) {
        String id = PlaylistUrlSupport.pathNumericId(input, PLAYLIST_HOSTS, PLAYLIST_PATH, "playlistId", "id", "pid");
        return new com.gitsilence.musicbox.ui.catalog.PlaylistImportRef(platform(), id,
                "https://www.kuwo.cn/playlist_detail/" + id);
    }
    private CompletableFuture<JsonElement> search(String query, int page, int size, String type) {
        return http.getJson(SEARCH, CatalogHttp.params("client", "kt", "all", query, "pn", page - 1, "rn", size,
                "uid", 0, "ver", "kwplayer_ar_9.2.2.1", "vipver", 1, "show_copyright_off", 1, "newver", 1,
                "ft", type, "cluster", 0, "strategy", 2012, "encoding", "utf8", "rformat", "json", "vermerge", 1, "mobi", 1));
    }
    static List<CatalogTrack> parseTracks(JsonArray values) {
        List<CatalogTrack> out = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            String rid = first(item, "MUSICRID", "musicrid");
            if (rid.isBlank()) { String numeric = first(item, "DC_TARGETID", "id"); rid = numeric.isBlank() ? "" : "MUSIC_" + numeric; }
            String formats = first(item, "MINFO", "N_MINFO").toLowerCase(Locale.ROOT);
            String cover = first(item, "web_albumpic_short", "pic");
            if (!cover.isBlank() && !cover.startsWith("http")) cover = "https://img4.kuwo.cn/star/albumcover/" + cover;
            String h128 = formats.contains("bitrate:128") ? rid : "";
            String h320 = formats.contains("bitrate:320") ? rid : "";
            String flac = formats.contains("format:flac") ? rid : "";
            out.add(new CatalogTrack(MusicPlatform.KUWO, rid, first(item, "SONGNAME", "name"),
                    first(item, "ARTIST", "artist"), first(item, "ALBUM", "album"), integer(item, "DURATION", "duration"),
                    cover, h128, h320, flac, ""));
        }
        return out;
    }
    static List<CatalogPlaylist> parsePlaylists(JsonArray values) {
        List<CatalogPlaylist> out = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            out.add(new CatalogPlaylist(MusicPlatform.KUWO, first(item, "playlistid", "DC_TARGETID"),
                    JsonSupport.string(item, "name"), JsonSupport.string(item, "nickname"), first(item, "hts_pic", "pic"),
                    integer(item, "songnum"), number(item, "playcnt"), JsonSupport.string(item, "intro")));
        }
        return out;
    }
    private static JsonObject root(JsonElement json) {
        if (!json.isJsonObject()) throw new CatalogException("Unexpected Kuwo response");
        return json.getAsJsonObject();
    }
    private static String first(JsonObject item, String... names) { for (String n : names) { String v=JsonSupport.string(item,n); if(!v.isBlank()) return v; } return ""; }
    private static int integer(JsonObject item, String... names) { return (int)Math.min(Integer.MAX_VALUE, number(item,names)); }
    private static long number(JsonObject item, String... names) { for(String n:names){long v=JsonSupport.number(item,n);if(v!=0)return v;}return 0; }
}
