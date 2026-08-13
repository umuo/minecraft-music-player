package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestTrackPayload(BlockPos pos, TrackRef track) implements CustomPacketPayload {
    public static final Type<RequestTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "request_track")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                PayloadCodecs.writeTrack(buffer, payload.track);
            },
            buffer -> new RequestTrackPayload(buffer.readBlockPos(), PayloadCodecs.readTrack(buffer))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
