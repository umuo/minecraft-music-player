package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<StopRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "stop_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StopRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos),
            buffer -> new StopRequestPayload(buffer.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
