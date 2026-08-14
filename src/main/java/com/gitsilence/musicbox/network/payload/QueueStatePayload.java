package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QueueStatePayload(BlockPos pos, List<TrackRef> tracks) implements CustomPacketPayload {
    public static final Type<QueueStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "queue_state"));
    public QueueStatePayload { tracks = List.copyOf(tracks); }
    public static final StreamCodec<RegistryFriendlyByteBuf, QueueStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.tracks.size());
                payload.tracks.forEach(track -> PayloadCodecs.writeTrack(buffer, track));
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                int size = Math.min(buffer.readVarInt(), 500);
                List<TrackRef> tracks = new ArrayList<>(size);
                for (int index = 0; index < size; index++) tracks.add(PayloadCodecs.readTrack(buffer));
                return new QueueStatePayload(pos, tracks);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
