package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Adapter for Migu's public catalog search. The retired playlist-detail route is not emulated. */
public final class MiguCatalogProvider implements MusicCatalogProvider {
    private static final String SEARCH = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do";
    private static final String DETAIL_INFO = "https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0";
    private static final String DETAIL_TRACKS = "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0";
    private static final Set<String> PLAYLIST_HOSTS = Set.of("music.migu.cn", "h5.nf.migu.cn", "m.music.migu.cn");
    private static final java.util.regex.Pattern PLAYLIST_PATH = java.util.regex.Pattern.compile("/(?:music/)?playlist/(\\d+)");
    private static final Map<String, String> HEADERS = Map.of("Referer", "https://music.migu.cn/");
    private final CatalogHttp http = new CatalogHttp();

    @Override public MusicPlatform platform() { return MusicPlatform.MIGU; }
    @Override public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        return request(query, page, pageSize, "{\"song\":1}").thenApply(json -> {
            JsonObject data = JsonSupport.object(root(json), "songResultData");
            return new PageResult<>(parseTracks(JsonSupport.array(data, "result")), page, pageSize,
                    JsonSupport.number(data, "totalCount"));
        });
    }
    @Override public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        String keyword = query == null || query.isBlank() ? "流行" : query;
        return request(keyword, page, pageSize, "{\"songlist\":1}").thenApply(json -> {
            JsonObject data = JsonSupport.object(root(json), "songListResultData");
            return new PageResult<>(parsePlaylists(JsonSupport.array(data, "result")), page, pageSize,
                    JsonSupport.number(data, "totalCount"));
        });
    }
    private static final List<CatalogPlaylist> RANKINGS = List.of(
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_27553319", "新歌榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_27186466", "热歌榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_27553408", "原创榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_75959118", "音乐风向榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_76557036", "彩铃分贝榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_76557745", "会员臻爱榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_23189800", "港台榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_23189399", "内地榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_19190036", "欧美榜", "咪咕音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.MIGU, "RANKING_83176390", "国风金曲榜", "咪咕音乐", "", 100, 0, "")
    );

    @Override public CompletableFuture<PageResult<CatalogPlaylist>> rankings(int page, int pageSize) {
        return CompletableFuture.completedFuture(new PageResult<>(RANKINGS, 1, RANKINGS.size(), RANKINGS.size()));
    }

    @Override public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        if (playlist.id().startsWith("RANKING_")) {
            String bangid = playlist.id().substring(8);
            return http.getJson("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querytoptlistdetails.do", CatalogHttp.params("topListId", bangid, "pageNo", 1, "pageSize", 100), HEADERS)
                    .thenApply(json -> {
                        JsonObject data = JsonSupport.object(root(json), "columnInfo");
                        JsonObject contents = JsonSupport.object(data, "contents");
                        return new PlaylistDetail(playlist, parseTracks(JsonSupport.array(contents, "songList")));
                    });
        }
        var info = http.getJson(DETAIL_INFO, CatalogHttp.params("playlistId", playlist.id()), HEADERS);
        var tracks = http.getJson(DETAIL_TRACKS, CatalogHttp.params("playlistId", playlist.id(), "pageNo", 1,
                "pageSize", 500), HEADERS);
        return info.thenCombine(tracks, (infoJson, trackJson) -> parseDetail(playlist.id(), infoJson, trackJson));
    }
    @Override public com.gitsilence.musicbox.ui.catalog.PlaylistImportRef normalizePlaylist(String input) {
        String id = PlaylistUrlSupport.pathNumericId(input, PLAYLIST_HOSTS, PLAYLIST_PATH, "playlistId", "id");
        return new com.gitsilence.musicbox.ui.catalog.PlaylistImportRef(platform(), id,
                "https://music.migu.cn/v3/music/playlist/" + id);
    }

    static PlaylistDetail parseDetail(String id, JsonElement infoJson, JsonElement trackJson) {
        JsonObject infoRoot = requireObject(infoJson, "Migu playlist info");
        JsonObject info = JsonSupport.object(infoRoot, "data");
        if (info.entrySet().isEmpty()) info = infoRoot;
        JsonObject trackRoot = requireObject(trackJson, "Migu playlist tracks");
        JsonObject trackData = JsonSupport.object(trackRoot, "data");
        JsonArray values = JsonSupport.array(trackData, "songList");
        if (values.isEmpty()) values = JsonSupport.array(trackData, "items");
        if (values.isEmpty()) values = JsonSupport.array(trackData, "songs");
        if (values.isEmpty()) values = JsonSupport.array(trackRoot, "items");
        List<CatalogTrack> tracks = parseTracks(values);
        String name = first(info, "playlistName", "name", "title");
        String creator = first(info, "ownerName", "createUserName", "creatorName", "userName");
        String cover = first(info, "playlistPicUrl", "img", "image");
        if (cover.isBlank()) cover = first(JsonSupport.object(info, "imgItem"), "img");
        long playCount = JsonSupport.number(info, "playCount");
        if (playCount == 0) playCount = JsonSupport.number(JsonSupport.object(info, "opNumItem"), "playNum");
        CatalogPlaylist playlist = new CatalogPlaylist(MusicPlatform.MIGU, id, name, creator, cover,
                tracks.size(), playCount, first(info, "summary", "description"));
        if (name.isBlank()) throw new CatalogException("Migu public playlist detail response was unavailable");
        return new PlaylistDetail(playlist, tracks);
    }
    private CompletableFuture<JsonElement> request(String query, int page, int size, String searchSwitch) {
        return http.getJson(SEARCH, CatalogHttp.params("text", query, "pageNo", page, "pageSize", size,
                "searchSwitch", searchSwitch), HEADERS);
    }
    static List<CatalogTrack> parseTracks(JsonArray values) {
        List<CatalogTrack> out = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            String id = JsonSupport.string(item, "copyrightId");
            if (id.isBlank()) id = JsonSupport.string(item, "contentId");
            Set<String> formats = new HashSet<>();
            for (JsonElement format : JsonSupport.array(item, "newRateFormats")) if (format.isJsonObject())
                formats.add(JsonSupport.string(format.getAsJsonObject(), "formatType"));
            JsonArray images = JsonSupport.array(item, "imgItems");
            String cover = images.isEmpty() || !images.get(0).isJsonObject() ? "" : JsonSupport.string(images.get(0).getAsJsonObject(), "img");
            JsonArray albums = JsonSupport.array(item, "albums");
            String album = albums.isEmpty() || !albums.get(0).isJsonObject() ? "" : JsonSupport.string(albums.get(0).getAsJsonObject(), "name");
            out.add(new CatalogTrack(MusicPlatform.MIGU, id, JsonSupport.string(item, "name"),
                    JsonSupport.joinedNames(JsonSupport.array(item, "singers"), "name"), album, 0, cover,
                    formats.contains("PQ") || formats.contains("LQ") ? id : "", formats.contains("HQ") ? id : "",
                    formats.contains("SQ") ? id : "", formats.contains("ZQ") ? id : ""));
        }
        return out;
    }
    static List<CatalogPlaylist> parsePlaylists(JsonArray values) {
        List<CatalogPlaylist> out = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            out.add(new CatalogPlaylist(MusicPlatform.MIGU, JsonSupport.string(item, "id"), JsonSupport.string(item, "name"), "",
                    JsonSupport.string(item, "musicListPicUrl"), JsonSupport.integer(item, "musicNum"),
                    JsonSupport.number(item, "playNum"), ""));
        }
        return out;
    }
    private static JsonObject root(JsonElement json) {
        if (!json.isJsonObject()) throw new CatalogException("Unexpected Migu response");
        JsonObject root=json.getAsJsonObject();
        if (!"000000".equals(JsonSupport.string(root,"code"))) throw new CatalogException("Migu rejected the request");
        return root;
    }
    private static JsonObject requireObject(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new CatalogException("Unexpected " + label + " response");
        return value.getAsJsonObject();
    }
    private static String first(JsonObject item, String... names) {
        for (String name : names) { String value = JsonSupport.string(item, name); if (!value.isBlank()) return value; }
        return "";
    }
}
