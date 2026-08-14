package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QueueRequestPayload(BlockPos pos, Operation operation, int index, TrackRef track)
        implements CustomPacketPayload {
    public enum Operation { ADD, REMOVE, SKIP, CLEAR }
    public static final Type<QueueRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "queue_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QueueRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeEnum(payload.operation);
                buffer.writeVarInt(payload.index);
                PayloadCodecs.writeTrack(buffer, payload.track);
            },
            buffer -> new QueueRequestPayload(buffer.readBlockPos(), buffer.readEnum(Operation.class),
                    buffer.readVarInt(), PayloadCodecs.readTrack(buffer)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
