package com.gitsilence.musicbox.network;

import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.payload.RequestTrackPayload;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.network.payload.StopRequestPayload;
import com.gitsilence.musicbox.network.payload.StopTrackPayload;
import com.gitsilence.musicbox.network.payload.QueueRequestPayload;
import com.gitsilence.musicbox.network.payload.QueueStatePayload;
import com.gitsilence.musicbox.network.payload.*;
import com.gitsilence.musicbox.server.playlist.SharedPlaylistService;
import com.gitsilence.musicbox.playback.TrackRef;
import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.server.resolver.ServerPlaybackResolver;
import com.gitsilence.musicbox.server.resolver.ResolverSourceConfig;
import com.gitsilence.musicbox.server.resolver.ResolverSourceRegistry;
import com.gitsilence.musicbox.server.resolver.ResolverSourceSelector;
import net.minecraft.core.BlockPos;
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

        ResolverSourceConfig source;
        try {
            source = selectSource(payload.track(), player);
        } catch (IllegalArgumentException error) {
            rejectSource(player, error);
            return;
        }
        musicBox.stop();
        sendNearby(player.serverLevel(), payload.pos(), new StopTrackPayload(payload.pos()));
        resolveAndStart(player.serverLevel(), payload.pos(), musicBox, payload.track(), source, player);
    }

    public static void stopTrack(StopRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !canControl(player, payload.pos())) {
            return;
        }
        stop(player.serverLevel(), payload.pos());
    }

    public static void stop(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MusicBoxBlockEntity musicBox) {
            musicBox.stop();
        }
        sendNearby(level, pos, new StopTrackPayload(pos));
    }

    public static void queueRequest(QueueRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !canControl(player, payload.pos())) return;
        if (!(player.level().getBlockEntity(payload.pos()) instanceof MusicBoxBlockEntity musicBox)) return;
        boolean changed;
        if (payload.operation() == QueueRequestPayload.Operation.ADD) {
            try {
                selectSource(payload.track(), player);
            } catch (IllegalArgumentException error) {
                rejectSource(player, error);
                return;
            }
        }
        changed = switch (payload.operation()) {
            case ADD -> musicBox.enqueue(payload.track(), MusicBoxConfig.MAX_QUEUE_SIZE.getAsInt());
            case REMOVE -> musicBox.removeQueued(payload.index());
            case CLEAR -> { musicBox.clearQueue(); yield true; }
            case SKIP -> { advance(player.serverLevel(), payload.pos(), musicBox); yield true; }
        };
        if (changed) sendNearby(player.serverLevel(), payload.pos(), new QueueStatePayload(payload.pos(), musicBox.queue()));
    }

    public static void sharedPlaylists(SharedPlaylistsRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendShared(player, "");
    }

    public static void importPlaylist(ImportPlaylistPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(SharedPlaylistService.REQUIRED_PERMISSION)) {
            sendShared(player, "permission_denied");
            return;
        }
        SharedPlaylistService service = SharedPlaylistService.get(player.server);
        service.importPlaylist(payload.platform(), payload.urlOrId(), payload.displayName())
                .whenComplete((playlist, error) -> player.server.execute(() -> {
                    if (error != null) {
                        MusicBoxMod.LOGGER.warn("Shared playlist import rejected for {}: {}", player.getGameProfile().getName(), rootMessage(error));
                        sendShared(player, "import_failed");
                    } else broadcastShared(player.server, "imported");
                }));
    }

    public static void deleteSharedPlaylist(DeleteSharedPlaylistPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(SharedPlaylistService.REQUIRED_PERMISSION)) {
            sendShared(player, "permission_denied");
            return;
        }
        boolean deleted;
        try { deleted = SharedPlaylistService.get(player.server).delete(payload.key()); }
        catch (RuntimeException error) { deleted = false; MusicBoxMod.LOGGER.error("Could not delete shared playlist", error); }
        if (deleted) broadcastShared(player.server, "deleted"); else sendShared(player, "delete_failed");
    }

    private static void sendShared(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new SharedPlaylistsPayload(
                SharedPlaylistService.get(player.server).snapshot(), message));
    }

    private static void broadcastShared(net.minecraft.server.MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendShared(player, message);
    }

    public static void advance(ServerLevel level, net.minecraft.core.BlockPos pos, MusicBoxBlockEntity musicBox) {
        TrackRef next = musicBox.pollQueue();
        if (next == null) {
            musicBox.stop();
            sendNearby(level, pos, new StopTrackPayload(pos));
        } else {
            musicBox.stop();
            sendNearby(level, pos, new StopTrackPayload(pos));
            try {
                ResolverSourceConfig source = ResolverSourceSelector.select(
                        ResolverSourceRegistry.configuredSources(), next, 4);
                resolveAndStart(level, pos, musicBox, next, source, null);
            } catch (IllegalArgumentException error) {
                MusicBoxMod.LOGGER.warn("Queued track rejected: {}", error.getMessage());
                advance(level, pos, musicBox);
                return;
            }
        }
        sendNearby(level, pos, new QueueStatePayload(pos, musicBox.queue()));
    }

    private static void resolveAndStart(ServerLevel level, net.minecraft.core.BlockPos pos,
                                        MusicBoxBlockEntity musicBox, TrackRef track, ResolverSourceConfig source,
                                        ServerPlayer requester) {
        long generation = musicBox.beginResolution();
        MusicBoxMod.LOGGER.info("Resolving track '{}' (source={}, id={}) via resolver '{}'...", track.title(), track.source(), track.trackId(), source.id());
        try {
            new ServerPlaybackResolver(source).resolve(track).whenComplete((resolved, error) -> level.getServer().execute(() -> {
                if (level.getBlockEntity(pos) != musicBox || !musicBox.isCurrentResolution(generation)) return;
                if (error != null) {
                    musicBox.stop();
                    MusicBoxMod.LOGGER.warn("Server resolver failed for {}", track.title(), error);
                    if (requester != null && requester.connection != null) requester.displayClientMessage(
                            Component.translatable("message.musicbox.resolve_failed", rootMessage(error)), false);
                    sendNearby(level, pos, new StopTrackPayload(pos));
                    return;
                }
                MusicBoxMod.LOGGER.info("Server resolved track '{}' -> Audio URL: {}", track.title(), resolved.audioUrl());
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

    private static ResolverSourceConfig selectSource(TrackRef track, ServerPlayer player) {
        int permission = 0;
        for (int level = 4; level >= 1; level--) {
            if (player.hasPermissions(level)) { permission = level; break; }
        }
        return ResolverSourceSelector.select(ResolverSourceRegistry.configuredSources(), track, permission);
    }

    private static void rejectSource(ServerPlayer player, IllegalArgumentException error) {
        MusicBoxMod.LOGGER.warn("Rejected resolver source request from {}: {}", player.getGameProfile().getName(),
                error.getMessage());
        player.displayClientMessage(Component.translatable("message.musicbox.source_rejected"), true);
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
