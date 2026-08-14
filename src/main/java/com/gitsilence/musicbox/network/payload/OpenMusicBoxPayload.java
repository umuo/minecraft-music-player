package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.ui.catalog.ResolverSourceInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMusicBoxPayload(BlockPos pos, List<ResolverSourceInfo> sources) implements CustomPacketPayload {
    public OpenMusicBoxPayload { sources = List.copyOf(sources); }
    public static final Type<OpenMusicBoxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "open_music_box")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMusicBoxPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.sources.size());
                payload.sources.forEach(source -> PayloadCodecs.writePublicSource(buffer, source));
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                int size = buffer.readVarInt();
                if (size < 0 || size > 64) throw new IllegalArgumentException("Invalid resolver source count");
                List<ResolverSourceInfo> sources = new ArrayList<>(size);
                for (int i = 0; i < size; i++) sources.add(PayloadCodecs.readPublicSource(buffer));
                return new OpenMusicBoxPayload(pos, sources);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
