package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.gitsilence.musicbox.ui.catalog.ResolverSourceInfo;

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
}
