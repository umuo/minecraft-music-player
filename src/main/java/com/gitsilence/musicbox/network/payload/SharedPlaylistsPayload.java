package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.server.playlist.SharedPlaylist;
import com.gitsilence.musicbox.server.playlist.SharedTrack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SharedPlaylistsPayload(List<SharedPlaylist> playlists, String message) implements CustomPacketPayload {
    public SharedPlaylistsPayload { playlists = List.copyOf(playlists); message = message == null ? "" : message; }
    public static final Type<SharedPlaylistsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "shared_playlists"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SharedPlaylistsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.playlists.size());
                for (SharedPlaylist playlist : payload.playlists) PayloadCodecs.writeSharedPlaylist(buffer, playlist);
                buffer.writeUtf(payload.message, 256);
            }, buffer -> {
                int count = PayloadCodecs.checkedCount(buffer.readVarInt(), 64, "shared playlist");
                List<SharedPlaylist> values = new ArrayList<>(count);
                for (int i=0;i<count;i++) values.add(PayloadCodecs.readSharedPlaylist(buffer));
                return new SharedPlaylistsPayload(values, buffer.readUtf(256));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
