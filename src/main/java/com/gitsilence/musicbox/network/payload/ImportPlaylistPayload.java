package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.MusicBoxMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImportPlaylistPayload(String platform, String urlOrId, String displayName) implements CustomPacketPayload {
    public ImportPlaylistPayload { platform = safe(platform, 16); urlOrId = safe(urlOrId, 512); displayName = safe(displayName, 128); }
    public static final Type<ImportPlaylistPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MusicBoxMod.MOD_ID, "import_playlist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImportPlaylistPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeUtf(payload.platform, 16); buffer.writeUtf(payload.urlOrId, 512); buffer.writeUtf(payload.displayName, 128); },
            buffer -> new ImportPlaylistPayload(buffer.readUtf(16), buffer.readUtf(512), buffer.readUtf(128)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static String safe(String value, int max) { String text=value==null?"":value.strip(); return text.length()<=max?text:text.substring(0,max); }
}
