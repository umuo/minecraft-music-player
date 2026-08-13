package com.gitsilence.musicbox.client;

import com.gitsilence.musicbox.client.playback.ClientPlaybackManager;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.payload.OpenMusicBoxPayload;
import com.gitsilence.musicbox.network.payload.RequestTrackPayload;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.network.payload.StopRequestPayload;
import com.gitsilence.musicbox.network.payload.StopTrackPayload;
import com.gitsilence.musicbox.playback.TrackRef;
import com.gitsilence.musicbox.ui.catalog.CatalogTrack;
import com.gitsilence.musicbox.ui.screen.MusicBrowserScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void openScreen(OpenMusicBoxPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new MusicBrowserScreen(
                MusicBoxConfig.DEFAULT_QUALITY.get(),
                (track, quality) -> PacketDistributor.sendToServer(new RequestTrackPayload(
                        payload.pos(), toTrackRef(track, quality)
                )),
                () -> PacketDistributor.sendToServer(new StopRequestPayload(payload.pos()))
        ));
    }

    public static void startTrack(StartTrackPayload payload, IPayloadContext context) {
        ClientPlaybackManager.start(payload);
    }

    public static void stopTrack(StopTrackPayload payload, IPayloadContext context) {
        ClientPlaybackManager.stop(payload.pos());
    }

    private static TrackRef toTrackRef(CatalogTrack track, String quality) {
        return new TrackRef(
                track.platform().source(),
                track.id(),
                track.title(),
                track.artist(),
                track.album(),
                track.durationSeconds(),
                track.hash128(),
                track.hash320(),
                track.hashFlac(),
                track.hashHires(),
                quality
        );
    }
}
