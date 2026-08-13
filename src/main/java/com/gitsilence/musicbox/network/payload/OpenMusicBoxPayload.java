package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMusicBoxPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenMusicBoxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "open_music_box")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMusicBoxPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.pos),
            buffer -> new OpenMusicBoxPayload(buffer.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
