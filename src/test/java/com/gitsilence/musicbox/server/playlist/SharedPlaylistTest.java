package com.gitsilence.musicbox.server.playlist;

import com.gitsilence.musicbox.network.payload.SharedPlaylistsPayload;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class SharedPlaylistTest {
    public static void main(String[] args) throws Exception {
        persistence(); permissionAndPlatform(); publicPayload();
        System.out.println("Shared playlist persistence, permission and secret-free payload checks passed");
    }
    private static void persistence() throws Exception {
        var directory=Files.createTempDirectory("musicbox-playlists-");
        var file=directory.resolve("shared.json"); var store=new SharedPlaylistStore(file);
        SharedPlaylist original=playlist(); store.save(List.of(original));
        check(store.load().equals(List.of(original)),"playlist did not survive reload");
        String json=Files.readString(file);
        check(!json.contains("token")&&!json.contains("resolver")&&!json.contains("playbackApiUrl"),"persistence leaked secret fields");
        Files.deleteIfExists(file); Files.deleteIfExists(directory);
    }
    private static void permissionAndPlatform() {
        check(!SharedPlaylistService.canManage(0)&&!SharedPlaylistService.canManage(1),"ordinary player can manage");
        check(SharedPlaylistService.canManage(2)&&SharedPlaylistService.canManage(4),"operator cannot manage");
        try{MusicPlatform.fromSource("spotify");throw new AssertionError("unknown platform accepted");}
        catch(IllegalArgumentException expected){}
    }
    private static void publicPayload() {
        SharedPlaylistsPayload original=new SharedPlaylistsPayload(List.of(playlist()),"");
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SharedPlaylistsPayload.STREAM_CODEC.encode(buffer,original);
        SharedPlaylistsPayload decoded=SharedPlaylistsPayload.STREAM_CODEC.decode(buffer);
        check(decoded.equals(original),"shared payload did not round-trip");
        String json=new Gson().toJson(decoded);
        check(!json.contains("secret-token")&&!json.contains("127.0.0.1")&&!json.contains("resolver"),"shared payload leaked secrets");
        check(SharedPlaylistService.estimatedBytes(decoded.playlists())<SharedPlaylistService.MAX_PUBLIC_PAYLOAD_BYTES,"fixture exceeds limit");
    }
    private static SharedPlaylist playlist(){return new SharedPlaylist("wy:22","wy","22","https://music.163.com/#/playlist?id=22",
            "Fixture","Creator","https://p1.music.126.net/a.jpg",1234,List.of(new SharedTrack("221","Song","Artist","Album",120,
            "https://p1.music.126.net/b.jpg","","","","")));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
