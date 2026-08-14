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
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KugouCatalogProvider implements MusicCatalogProvider {
    private static final Set<String> PLAYLIST_HOSTS = Set.of("kugou.com", "www.kugou.com", "m.kugou.com", "m3ws.kugou.com");
    private static final Pattern PLAYLIST_PATH = Pattern.compile("/(?:plist/list|songlist)/(?:gcid_)?([A-Za-z0-9_-]+)");
    private static final String TRACK_SEARCH_URL = "https://songsearch.kugou.com/song_search_v2";
    private static final String PLAYLIST_SEARCH_URL = "https://msearchretry.kugou.com/api/v3/search/special";
    private static final Pattern DETAIL_DATA = Pattern.compile(
            "var\\s+data\\s*=\\s*(\\[.*?])\\s*(?:;|,\\s*specialData\\s*=)",
            Pattern.DOTALL
    );
    private static final Pattern META_DESCRIPTION = Pattern.compile("<meta\\s+name=\"description\"\\s+content=\"(.*?)\"", Pattern.DOTALL);
    private final CatalogHttp http = new CatalogHttp();

    @Override
    public MusicPlatform platform() {
        return MusicPlatform.KUGOU;
    }

    @Override
    public CompletableFuture<PageResult<CatalogTrack>> searchTracks(String query, int page, int pageSize) {
        return http.getJson(TRACK_SEARCH_URL, CatalogHttp.params(
                        "keyword", query,
                        "page", page,
                        "pagesize", pageSize,
                        "userid", 0,
                        "platform", "WebFilter",
                        "filter", 2,
                        "iscorrection", 1,
                        "privilege_filter", 0,
                        "area_code", 1
                ))
                .thenApply(json -> {
                    JsonObject root = root(json);
                    JsonObject data = JsonSupport.object(root, "data");
                    return new PageResult<>(parseSearchTracks(JsonSupport.array(data, "lists")), page, pageSize,
                            JsonSupport.number(data, "total"));
                });
    }

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> playlists(String query, int page, int pageSize) {
        if (query == null || query.isBlank()) {
            URI uri = URI.create("https://m.kugou.com/plist/index&json=true&page=" + page);
            return http.getText(uri).thenApply(body -> {
                JsonObject root = root(JsonParser.parseString(body));
                JsonObject list = JsonSupport.object(JsonSupport.object(root, "plist"), "list");
                List<CatalogPlaylist> items = parsePlaylists(JsonSupport.array(list, "info"));
                return new PageResult<>(items, page, Math.max(1, items.size()), JsonSupport.number(list, "total"));
            });
        }
        return http.getJson(PLAYLIST_SEARCH_URL, CatalogHttp.params(
                        "keyword", query,
                        "page", page,
                        "pagesize", pageSize,
                        "showtype", 10,
                        "filter", 0,
                        "version", 7910,
                        "sver", 2
                ))
                .thenApply(json -> {
                    JsonObject data = JsonSupport.object(root(json), "data");
                    return new PageResult<>(parsePlaylists(JsonSupport.array(data, "info")), page, pageSize,
                            JsonSupport.number(data, "total"));
                });
    }

    private static final List<CatalogPlaylist> RANKINGS = List.of(
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_8888", "TOP500", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_6666", "飙升榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_52144", "抖音热歌榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_24971", "DJ热歌榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_23784", "网络红歌榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_31308", "内地榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_33160", "电音榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_31313", "香港地区榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_31310", "欧美榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_31311", "韩国榜", "酷狗音乐", "", 100, 0, ""),
            new CatalogPlaylist(MusicPlatform.KUGOU, "RANKING_31312", "日本榜", "酷狗音乐", "", 100, 0, "")
    );

    @Override
    public CompletableFuture<PageResult<CatalogPlaylist>> rankings(int page, int pageSize) {
        return CompletableFuture.completedFuture(new PageResult<>(RANKINGS, 1, RANKINGS.size(), RANKINGS.size()));
    }

    @Override
    public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        if (playlist.id().startsWith("RANKING_")) {
            String bangid = playlist.id().substring(8);
            return http.getJson("http://mobilecdnbj.kugou.com/api/v3/rank/song", CatalogHttp.params("version", 9108, "ranktype", 1, "plat", 0, "pagesize", 100, "area_code", 1, "page", 1, "rankid", bangid, "with_res_tag", 0, "show_portrait_mv", 1))
                    .thenApply(json -> {
                        JsonObject data = JsonSupport.object(root(json), "data");
                        return new PlaylistDetail(playlist, parseRankingTracks(JsonSupport.array(data, "info")));
                    });
        }
        URI uri = URI.create("https://m.kugou.com/plist/list/" + playlist.id() + "/?json=true");
        return http.getText(uri).thenApply(html -> parseDetail(playlist, html));
    }

    @Override public com.gitsilence.musicbox.ui.catalog.PlaylistImportRef normalizePlaylist(String input) {
        String id = PlaylistUrlSupport.tokenIdOrUrl(input, PLAYLIST_HOSTS, PLAYLIST_PATH,
                "specialid", "global_collection_id");
        if (!id.chars().allMatch(Character::isDigit)) {
            throw new CatalogException("Kugou public detail import currently requires a numeric specialid");
        }
        return new com.gitsilence.musicbox.ui.catalog.PlaylistImportRef(platform(), id,
                "https://m.kugou.com/plist/list/" + id + "/");
    }

    private static JsonObject root(JsonElement json) {
        if (!json.isJsonObject()) throw new CatalogException("Unexpected Kugou response");
        JsonObject root = json.getAsJsonObject();
        long status = JsonSupport.number(root, "status");
        long errorCode = JsonSupport.number(root, "error_code");
        long errCode = JsonSupport.number(root, "errcode");
        if (status != 0 && status != 1) throw new CatalogException("Kugou returned status " + status);
        if (errorCode != 0 || errCode != 0) throw new CatalogException("Kugou rejected the request");
        return root;
    }

    private static List<CatalogTrack> parseSearchTracks(JsonArray rawTracks) {
        List<CatalogTrack> tracks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : rawTracks) {
            if (!element.isJsonObject()) continue;
            addSearchTrack(tracks, seen, element.getAsJsonObject());
            for (JsonElement child : JsonSupport.array(element.getAsJsonObject(), "Grp")) {
                if (child.isJsonObject()) addSearchTrack(tracks, seen, child.getAsJsonObject());
            }
        }
        return tracks;
    }

    private static List<CatalogTrack> parseRankingTracks(JsonArray rawTracks) {
        List<CatalogTrack> tracks = new ArrayList<>();
        for (JsonElement element : rawTracks) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String authorName = "";
            JsonArray authors = JsonSupport.array(item, "authors");
            if (authors != null && authors.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement a : authors) {
                    if (a.isJsonObject()) {
                        if (!sb.isEmpty()) sb.append("、");
                        sb.append(JsonSupport.string(a.getAsJsonObject(), "author_name"));
                    }
                }
                authorName = sb.toString();
            }
            if (authorName.isEmpty()) authorName = first(item, "author_name", "singername");
            
            long millis = number(item, "duration", "timelength");
            if (millis > 10_000) millis /= 1000;
            
            tracks.add(new CatalogTrack(
                    MusicPlatform.KUGOU,
                    first(item, "audio_id", "album_audio_id", "MixSongID", "ID"),
                    first(item, "songname", "audio_name", "SongName"),
                    authorName,
                    first(item, "remark", "album_name", "AlbumName"),
                    (int) Math.min(Integer.MAX_VALUE, millis),
                    nestedCover(item),
                    first(item, "hash", "FileHash"),
                    first(item, "320hash", "hash_320"),
                    first(item, "sqhash", "hash_flac"),
                    first(item, "high_hash", "hash_high")
            ));
        }
        return tracks;
    }

    private static void addSearchTrack(List<CatalogTrack> tracks, Set<String> seen, JsonObject item) {
        String id = first(item, "Audioid", "MixSongID", "ID");
        String hash128 = first(item, "FileHash", "hash");
        if (!seen.add(id + ':' + hash128)) return;
        tracks.add(new CatalogTrack(
                MusicPlatform.KUGOU,
                id,
                first(item, "SongName", "OriSongName"),
                first(item, "SingerName", "FileName"),
                first(item, "AlbumName", "Remark"),
                JsonSupport.integer(item, "Duration"),
                first(item, "Image", "AlbumImage"),
                hash128,
                first(item, "HQFileHash", "FileHash"),
                first(item, "SQFileHash", "HQFileHash"),
                first(item, "ResFileHash", "SQFileHash")
        ));
    }

    private static List<CatalogPlaylist> parsePlaylists(JsonArray rawPlaylists) {
        List<CatalogPlaylist> playlists = new ArrayList<>();
        for (JsonElement element : rawPlaylists) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            playlists.add(new CatalogPlaylist(
                    MusicPlatform.KUGOU,
                    first(item, "specialid", "id"),
                    first(item, "specialname", "name"),
                    first(item, "nickname", "username"),
                    first(item, "imgurl", "img"),
                    integer(item, "songcount", "song_count"),
                    number(item, "playcount", "total_play_count"),
                    first(item, "intro", "description")
            ));
        }
        return playlists;
    }

    static PlaylistDetail parseDetail(CatalogPlaylist playlist, String html) {
        Matcher matcher = DETAIL_DATA.matcher(html);
        if (!matcher.find()) throw new CatalogException("Kugou playlist page did not contain song data");
        JsonElement raw = JsonParser.parseString(matcher.group(1));
        if (!raw.isJsonArray()) throw new CatalogException("Invalid Kugou playlist song data");
        List<CatalogTrack> tracks = new ArrayList<>();
        for (JsonElement element : raw.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            long millis = number(item, "timelength", "duration");
            if (millis > 10_000) millis /= 1000;
            tracks.add(new CatalogTrack(
                    MusicPlatform.KUGOU,
                    first(item, "audio_id", "album_audio_id"),
                    first(item, "audio_name", "songname"),
                    first(item, "author_name", "singername"),
                    first(item, "album_name", "remark"),
                    (int) Math.min(Integer.MAX_VALUE, millis),
                    nestedCover(item),
                    first(item, "hash_128", "hash"),
                    first(item, "hash_320", "hash"),
                    first(item, "hash_flac", "hash"),
                    first(item, "hash_high", "hash_flac")
            ));
        }
        String description = playlist.description();
        Matcher descriptionMatcher = META_DESCRIPTION.matcher(html);
        if (descriptionMatcher.find()) description = JsonSupport.htmlDecode(descriptionMatcher.group(1));
        CatalogPlaylist detail = new CatalogPlaylist(
                playlist.platform(), playlist.id(), playlist.name(), playlist.creator(), playlist.coverUrl(),
                tracks.size(), playlist.playCount(), description
        );
        return new PlaylistDetail(detail, tracks);
    }

    private static String nestedCover(JsonObject item) {
        String direct = first(item, "img", "imgurl");
        if (!direct.isEmpty()) return direct;
        return first(JsonSupport.object(item, "trans_param"), "union_cover");
    }

    private static String first(JsonObject item, String... names) {
        for (String name : names) {
            String value = JsonSupport.string(item, name);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static int integer(JsonObject item, String... names) {
        return (int) Math.min(Integer.MAX_VALUE, number(item, names));
    }

    private static long number(JsonObject item, String... names) {
        for (String name : names) {
            long value = JsonSupport.number(item, name);
            if (value != 0) return value;
        }
        return 0;
    }
}
