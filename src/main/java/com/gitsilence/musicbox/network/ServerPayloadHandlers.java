package com.gitsilence.musicbox.network;

import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.payload.RequestTrackPayload;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.network.payload.StopRequestPayload;
import com.gitsilence.musicbox.network.payload.StopTrackPayload;
import com.gitsilence.musicbox.network.payload.QueueRequestPayload;
import com.gitsilence.musicbox.network.payload.QueueStatePayload;
import com.gitsilence.musicbox.playback.TrackRef;
import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.server.resolver.ServerPlaybackResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private ServerPayloadHandlers() {
    }

    public static void requestTrack(RequestTrackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !payload.track().isValid()) {
            return;
        }
        if (!canControl(player, payload.pos())) {
            player.displayClientMessage(Component.translatable("message.musicbox.too_far"), true);
            return;
        }
        if (!(player.level().getBlockEntity(payload.pos()) instanceof MusicBoxBlockEntity musicBox)) {
            return;
        }

        musicBox.stop();
        sendNearby(player.serverLevel(), payload.pos(), new StopTrackPayload(payload.pos()));
        resolveAndStart(player.serverLevel(), payload.pos(), musicBox, payload.track(), player);
    }

    public static void stopTrack(StopRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !canControl(player, payload.pos())) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos()) instanceof MusicBoxBlockEntity musicBox) {
            musicBox.stop();
            sendNearby(player.serverLevel(), payload.pos(), new StopTrackPayload(payload.pos()));
        }
    }

    public static void queueRequest(QueueRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !canControl(player, payload.pos())) return;
        if (!(player.level().getBlockEntity(payload.pos()) instanceof MusicBoxBlockEntity musicBox)) return;
        boolean changed = switch (payload.operation()) {
            case ADD -> musicBox.enqueue(payload.track(), MusicBoxConfig.MAX_QUEUE_SIZE.getAsInt());
            case REMOVE -> musicBox.removeQueued(payload.index());
            case CLEAR -> { musicBox.clearQueue(); yield true; }
            case SKIP -> { advance(player.serverLevel(), payload.pos(), musicBox); yield true; }
        };
        if (changed) sendNearby(player.serverLevel(), payload.pos(), new QueueStatePayload(payload.pos(), musicBox.queue()));
    }

    public static void advance(ServerLevel level, net.minecraft.core.BlockPos pos, MusicBoxBlockEntity musicBox) {
        TrackRef next = musicBox.pollQueue();
        if (next == null) {
            musicBox.stop();
            sendNearby(level, pos, new StopTrackPayload(pos));
        } else {
            musicBox.stop();
            resolveAndStart(level, pos, musicBox, next, null);
        }
        sendNearby(level, pos, new QueueStatePayload(pos, musicBox.queue()));
    }

    private static void resolveAndStart(ServerLevel level, net.minecraft.core.BlockPos pos,
                                        MusicBoxBlockEntity musicBox, TrackRef track, ServerPlayer requester) {
        long generation = musicBox.beginResolution();
        try {
            new ServerPlaybackResolver().resolve(track).whenComplete((resolved, error) -> level.getServer().execute(() -> {
                if (level.getBlockEntity(pos) != musicBox || !musicBox.isCurrentResolution(generation)) return;
                if (error != null) {
                    musicBox.stop();
                    MusicBoxMod.LOGGER.warn("Server resolver failed for {}", track.title(), error);
                    if (requester != null && requester.connection != null) requester.displayClientMessage(
                            Component.translatable("message.musicbox.resolve_failed", rootMessage(error)), false);
                    sendNearby(level, pos, new StopTrackPayload(pos));
                    return;
                }
                long start = level.getGameTime() + MusicBoxConfig.START_DELAY_TICKS.getAsInt();
                musicBox.start(track, start);
                sendNearby(level, pos, new StartTrackPayload(pos, track, start,
                        MusicBoxConfig.BROADCAST_RADIUS.getAsInt(), resolved.audioUrl(), resolved.lyrics()));
            }));
        } catch (Exception error) {
            musicBox.stop();
            MusicBoxMod.LOGGER.warn("Server resolver could not start for {}", track.title(), error);
            if (requester != null) requester.displayClientMessage(
                    Component.translatable("message.musicbox.resolve_failed", rootMessage(error)), false);
            sendNearby(level, pos, new StopTrackPayload(pos));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private static boolean canControl(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    public static void sendNearby(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        double radiusSquared = Math.pow(MusicBoxConfig.BROADCAST_RADIUS.getAsInt(), 2);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer listener : level.players()) {
            if (listener.distanceToSqr(center) <= radiusSquared) {
                PacketDistributor.sendToPlayer(listener, payload);
            }
        }
    }
}
