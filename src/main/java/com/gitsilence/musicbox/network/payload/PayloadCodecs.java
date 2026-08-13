package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.network.RegistryFriendlyByteBuf;

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
                buffer.readUtf(16)
        );
    }
}
