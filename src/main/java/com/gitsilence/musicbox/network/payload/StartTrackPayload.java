package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StartTrackPayload(BlockPos pos, TrackRef track, long startGameTime) implements CustomPacketPayload {
    public static final Type<StartTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "start_track")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StartTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                PayloadCodecs.writeTrack(buffer, payload.track);
                buffer.writeVarLong(payload.startGameTime);
            },
            buffer -> new StartTrackPayload(
                    buffer.readBlockPos(),
                    PayloadCodecs.readTrack(buffer),
                    buffer.readVarLong()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
