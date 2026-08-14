package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PlaylistImportProviderTest {
    public static void main(String[] args) throws Exception {
        canonicalization(); fixtures();
        System.out.println("Playlist URL policy and five platform fixture checks passed");
    }
    private static void canonicalization() {
        check(new NeteaseCatalogProvider().normalizePlaylist("https://music.163.com/#/playlist?id=22").canonicalUrl().equals("https://music.163.com/#/playlist?id=22"));
        check(new QqCatalogProvider().normalizePlaylist("https://y.qq.com/n/ryqq/playlist/33").playlistId().equals("33"));
        check(new KuwoCatalogProvider().normalizePlaylist("https://www.kuwo.cn/playlist_detail/44").playlistId().equals("44"));
        check(new MiguCatalogProvider().normalizePlaylist("https://h5.nf.migu.cn/app/v4/p/share/playlist/index.html?id=55").playlistId().equals("55"));
        check(new KugouCatalogProvider().normalizePlaylist("https://m.kugou.com/plist/list/66/").playlistId().equals("66"));
        rejects(() -> new NeteaseCatalogProvider().normalizePlaylist("https://evil.example/playlist?id=22"));
        rejects(() -> new QqCatalogProvider().normalizePlaylist("https://y.qq.com.evil.example/n/ryqq/playlist/33"));
        rejects(() -> new KuwoCatalogProvider().normalizePlaylist("file:///playlist/44"));
        rejects(() -> MusicPlatform.fromSource("unknown"));
    }
    private static void fixtures() throws IOException {
        PlaylistDetail kg = KugouCatalogProvider.parseDetail(new CatalogPlaylist(MusicPlatform.KUGOU,"66","KG List","","",0,0,""), text("kugou.html"));
        PlaylistDetail wy = NeteaseCatalogProvider.parseDetail(json("netease.json"));
        PlaylistDetail qq = QqCatalogProvider.parseDetail(json("qq.json"));
        PlaylistDetail kw = KuwoCatalogProvider.parseDetail(json("kuwo.json"));
        PlaylistDetail mg = MiguCatalogProvider.parseDetail("55", json("migu-info.json"), json("migu-tracks.json"));
        for (PlaylistDetail value : new PlaylistDetail[]{kg,wy,qq,kw,mg}) {
            check(!value.playlist().name().isBlank()); check(value.tracks().size()==1);
            check(!value.tracks().getFirst().id().isBlank() && !value.tracks().getFirst().title().isBlank());
        }
    }
    private static JsonElement json(String name) throws IOException { return JsonParser.parseString(text(name)); }
    private static String text(String name) throws IOException {
        try (var input=PlaylistImportProviderTest.class.getResourceAsStream("/playlists/"+name)) {
            if(input==null)throw new IOException(name);return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("unsafe input accepted");}catch(RuntimeException expected){}}
    private static void check(boolean value){if(!value)throw new AssertionError();}
}
