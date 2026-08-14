package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteSharedPlaylistPayload(String key) implements CustomPacketPayload {
    public DeleteSharedPlaylistPayload { key = key == null ? "" : key.strip(); }
    public static final Type<DeleteSharedPlaylistPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "delete_shared_playlist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteSharedPlaylistPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.key, 160), buffer -> new DeleteSharedPlaylistPayload(buffer.readUtf(160)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
