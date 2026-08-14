package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.gitsilence.musicbox.ui.catalog.ResolverSourceInfo;
import com.gitsilence.musicbox.server.playlist.SharedPlaylist;
import com.gitsilence.musicbox.server.playlist.SharedTrack;

final class PayloadCodecs {
    private PayloadCodecs() {
    }

    static void writeTrack(RegistryFriendlyByteBuf buffer, TrackRef track) {
        buffer.writeUtf(track.source(), 16);
        buffer.writeUtf(track.trackId(), 256);
        buffer.writeUtf(track.title(), 128);
        buffer.writeUtf(track.artist(), 128);
        buffer.writeUtf(track.album(), 128);
        buffer.writeVarInt(track.durationSeconds());
        buffer.writeUtf(track.hash128(), 128);
        buffer.writeUtf(track.hash320(), 128);
        buffer.writeUtf(track.hashFlac(), 128);
        buffer.writeUtf(track.hashHires(), 128);
        buffer.writeUtf(track.quality(), 16);
        buffer.writeUtf(track.sourceId(), 32);
    }

    static TrackRef readTrack(RegistryFriendlyByteBuf buffer) {
        return new TrackRef(
                buffer.readUtf(16),
                buffer.readUtf(256),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(16),
                buffer.readUtf(32)
        );
    }

    static void writePublicSource(RegistryFriendlyByteBuf buffer, ResolverSourceInfo source) {
        buffer.writeUtf(source.id(), 32);
        buffer.writeUtf(source.displayName(), 64);
        writeStrings(buffer, source.platforms());
        writeStrings(buffer, source.qualities());
        writeStrings(buffer, source.capabilities());
    }

    static ResolverSourceInfo readPublicSource(RegistryFriendlyByteBuf buffer) {
        return new ResolverSourceInfo(buffer.readUtf(32), buffer.readUtf(64), readStrings(buffer),
                readStrings(buffer), readStrings(buffer));
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, java.util.List<String> values) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> buffer.writeUtf(value, 32));
    }

    private static java.util.List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 16) throw new IllegalArgumentException("Invalid resolver capability list size");
        java.util.List<String> values = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buffer.readUtf(32));
        return java.util.List.copyOf(values);
    }

    static void writeSharedPlaylist(RegistryFriendlyByteBuf buffer, SharedPlaylist value) {
        buffer.writeUtf(value.key(),160); buffer.writeUtf(value.platform(),16); buffer.writeUtf(value.playlistId(),128);
        buffer.writeUtf(value.canonicalUrl(),512); buffer.writeUtf(value.name(),128); buffer.writeUtf(value.creator(),128);
        buffer.writeUtf(value.coverUrl(),512); buffer.writeLong(value.importedAtEpochMillis());
        buffer.writeVarInt(value.tracks().size());
        value.tracks().forEach(track -> writeSharedTrack(buffer, track));
    }
    static SharedPlaylist readSharedPlaylist(RegistryFriendlyByteBuf buffer) {
        String key=buffer.readUtf(160), platform=buffer.readUtf(16), id=buffer.readUtf(128), url=buffer.readUtf(512);
        String name=buffer.readUtf(128), creator=buffer.readUtf(128), cover=buffer.readUtf(512); long imported=buffer.readLong();
        int count=checkedCount(buffer.readVarInt(),500,"shared track"); java.util.List<SharedTrack> tracks=new java.util.ArrayList<>(count);
        for(int i=0;i<count;i++) tracks.add(readSharedTrack(buffer));
        return new SharedPlaylist(key,platform,id,url,name,creator,cover,imported,tracks);
    }
    private static void writeSharedTrack(RegistryFriendlyByteBuf b, SharedTrack t) {
        b.writeUtf(t.id(),256);b.writeUtf(t.title(),128);b.writeUtf(t.artist(),128);b.writeUtf(t.album(),128);
        b.writeVarInt(t.durationSeconds());b.writeUtf(t.coverUrl(),512);b.writeUtf(t.hash128(),128);
        b.writeUtf(t.hash320(),128);b.writeUtf(t.hashFlac(),128);b.writeUtf(t.hashHires(),128);
    }
    private static SharedTrack readSharedTrack(RegistryFriendlyByteBuf b) {
        return new SharedTrack(b.readUtf(256),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readVarInt(),
                b.readUtf(512),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readUtf(128));
    }
    static int checkedCount(int value,int max,String label){if(value<0||value>max)throw new IllegalArgumentException("Invalid "+label+" count");return value;}
}
