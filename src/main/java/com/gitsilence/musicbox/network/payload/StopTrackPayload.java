package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopTrackPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<StopTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "stop_track")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StopTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos),
            buffer -> new StopTrackPayload(buffer.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
