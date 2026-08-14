package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Adapter for Migu's public catalog search. The retired playlist-detail route is not emulated. */
public final class MiguCatalogProvider implements MusicCatalogProvider {
    private static final String SEARCH = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do";
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
    @Override public CompletableFuture<PlaylistDetail> playlistDetail(CatalogPlaylist playlist) {
        return CompletableFuture.failedFuture(new CatalogException(
                "Migu's public playlist-detail API is unavailable; no catalog result was fabricated"));
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
}
