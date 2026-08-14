package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SharedPlaylistsRequestPayload() implements CustomPacketPayload {
    public static final Type<SharedPlaylistsRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "shared_playlists_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SharedPlaylistsRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {}, buffer -> new SharedPlaylistsRequestPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
