package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StartTrackPayload(BlockPos pos, TrackRef track, long startGameTime, int broadcastRadius, String audioUrl, String lyrics) implements CustomPacketPayload {
    public static final Type<StartTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "start_track")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                PayloadCodecs.writeTrack(buffer, payload.track);
                buffer.writeVarLong(payload.startGameTime);
                buffer.writeVarInt(payload.broadcastRadius);
                buffer.writeUtf(payload.audioUrl, 4096);
                buffer.writeUtf(payload.lyrics, 1_048_576);
            },
            buffer -> new StartTrackPayload(
                    buffer.readBlockPos(),
                    PayloadCodecs.readTrack(buffer),
                    buffer.readVarLong(),
                    buffer.readVarInt(),
                    buffer.readUtf(4096),
                    buffer.readUtf(1_048_576)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
