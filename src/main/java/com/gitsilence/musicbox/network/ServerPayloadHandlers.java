package com.gitsilence.musicbox.network;

import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.payload.RequestTrackPayload;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.network.payload.StopRequestPayload;
import com.gitsilence.musicbox.network.payload.StopTrackPayload;
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

        long startGameTime = player.level().getGameTime() + MusicBoxConfig.START_DELAY_TICKS.getAsInt();
        musicBox.start(payload.track(), startGameTime);
        StartTrackPayload start = new StartTrackPayload(payload.pos(), payload.track(), startGameTime);
        sendNearby(player.serverLevel(), payload.pos(), start);
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

    private static boolean canControl(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    private static void sendNearby(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        double radiusSquared = Math.pow(MusicBoxConfig.BROADCAST_RADIUS.getAsInt(), 2);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer listener : level.players()) {
            if (listener.distanceToSqr(center) <= radiusSquared) {
                PacketDistributor.sendToPlayer(listener, payload);
            }
        }
    }
}
