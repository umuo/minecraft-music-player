package com.gitsilence.musicbox.network;

import com.gitsilence.musicbox.client.ClientPayloadHandlers;
import com.gitsilence.musicbox.network.payload.OpenMusicBoxPayload;
import com.gitsilence.musicbox.network.payload.RequestTrackPayload;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.network.payload.StopRequestPayload;
import com.gitsilence.musicbox.network.payload.StopTrackPayload;
import com.gitsilence.musicbox.network.payload.QueueRequestPayload;
import com.gitsilence.musicbox.network.payload.QueueStatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MusicBoxNetworking {
    private MusicBoxNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientboundHandlers(registrar);
        } else {
            registerClientboundPlaceholders(registrar);
        }

        registrar.playToServer(RequestTrackPayload.TYPE, RequestTrackPayload.STREAM_CODEC, ServerPayloadHandlers::requestTrack);
        registrar.playToServer(StopRequestPayload.TYPE, StopRequestPayload.STREAM_CODEC, ServerPayloadHandlers::stopTrack);
        registrar.playToServer(QueueRequestPayload.TYPE, QueueRequestPayload.STREAM_CODEC, ServerPayloadHandlers::queueRequest);
    }

    private static void registerClientboundHandlers(PayloadRegistrar registrar) {
        registrar.playToClient(OpenMusicBoxPayload.TYPE, OpenMusicBoxPayload.STREAM_CODEC, ClientPayloadHandlers::openScreen);
        registrar.playToClient(StartTrackPayload.TYPE, StartTrackPayload.STREAM_CODEC, ClientPayloadHandlers::startTrack);
        registrar.playToClient(StopTrackPayload.TYPE, StopTrackPayload.STREAM_CODEC, ClientPayloadHandlers::stopTrack);
        registrar.playToClient(QueueStatePayload.TYPE, QueueStatePayload.STREAM_CODEC, ClientPayloadHandlers::queueState);
    }

    private static void registerClientboundPlaceholders(PayloadRegistrar registrar) {
        registrar.playToClient(OpenMusicBoxPayload.TYPE, OpenMusicBoxPayload.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToClient(StartTrackPayload.TYPE, StartTrackPayload.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToClient(StopTrackPayload.TYPE, StopTrackPayload.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToClient(QueueStatePayload.TYPE, QueueStatePayload.STREAM_CODEC, (payload, context) -> {
        });
    }
}
