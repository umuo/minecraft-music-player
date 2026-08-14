package com.gitsilence.musicbox.client.render;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.client.lyrics.ClientLyricsManager;
import com.gitsilence.musicbox.client.playback.ClientPlaybackManager;
import com.gitsilence.musicbox.playback.TrackRef;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = MusicBoxMod.MOD_ID, value = Dist.CLIENT)
public final class MusicBoxWorldOverlayRenderer {
    private static final double MAX_DISTANCE = 24.0;
    private static final int MAX_TEXT_WIDTH = 150;
    private static final float SCALE = 0.016F;

    private MusicBoxWorldOverlayRenderer() { }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlaybackManager.PlaybackSnapshot state = ClientPlaybackManager.snapshot();
        if (state == null || minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) return;
        BlockPos pos = state.pos();
        if (!(minecraft.level.getBlockEntity(pos) instanceof MusicBoxBlockEntity)) return;

        Camera camera = event.getCamera();
        Vec3 anchor = Vec3.atCenterOf(pos).add(0.0, 1.15, 0.0);
        double distance = camera.getPosition().distanceTo(anchor);
        double limit = Math.min(MAX_DISTANCE, Math.max(1, state.broadcastRadius()));
        if (distance > limit || !event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(pos).inflate(0.25).move(0, 1.15, 0))) return;
        if (!hasLineOfSight(minecraft, camera.getPosition(), anchor, pos)) return;

        int alpha = Mth.clamp((int) (255.0 * Math.min(1.0, (limit - distance) / 5.0)), 48, 255);
        renderOverlay(event.getPoseStack(), camera, minecraft, state, anchor, alpha);
    }

    private static boolean hasLineOfSight(Minecraft minecraft, Vec3 from, Vec3 to, BlockPos source) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(source);
    }

    private static void renderOverlay(PoseStack pose, Camera camera, Minecraft minecraft,
                                      ClientPlaybackManager.PlaybackSnapshot state, Vec3 anchor, int alpha) {
        Vec3 cameraPos = camera.getPosition();
        pose.pushPose();
        pose.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
        pose.mulPose(camera.rotation());
        pose.scale(-SCALE, -SCALE, SCALE);

        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        TrackRef track = state.track();
        List<String> lines = new ArrayList<>();
        lines.add(track.artist().isBlank() ? track.title()
                : Component.translatable("overlay.musicbox.title_artist", track.title(), track.artist()).getString());

        PlaybackProgress.State progress = PlaybackProgress.calculate(minecraft.level.getGameTime(),
                state.startGameTime(), track.durationSeconds());
        lines.add(progressLine(progress));
        ClientLyricsManager.LyricsSnapshot lyrics = ClientLyricsManager.snapshot(minecraft.level.getGameTime());
        if (lyrics != null && lyrics.source().equals(state.pos())) lines.addAll(lyrics.lines());

        int y = 0;
        for (int index = 0; index < lines.size(); index++) {
            String clipped = font.plainSubstrByWidth(lines.get(index), MAX_TEXT_WIDTH);
            int color = (alpha << 24) | (index == 0 ? 0xF2C14E : index == 1 ? 0xD8DEE9 : 0xF0F1F4);
            int background = ((alpha * 110 / 255) << 24);
            float x = -font.width(clipped) / 2.0F;
            font.drawInBatch(clipped, x, y, color, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, background, 0x00F000F0);
            y += index == 1 ? 13 : 11;
        }
        buffers.endBatch();
        pose.popPose();
    }

    private static String progressLine(PlaybackProgress.State progress) {
        if (progress.buffering()) return Component.translatable("overlay.musicbox.buffering").getString();
        if (!progress.knownDuration()) return Component.translatable("overlay.musicbox.unknown_duration").getString();
        int filled = Mth.clamp(Math.round(progress.fraction() * 12), 0, 12);
        String bar = "■".repeat(filled) + "·".repeat(12 - filled);
        long elapsedSeconds = progress.elapsedTicks() / 20L;
        long durationSeconds = progress.durationTicks() / 20L;
        return Component.translatable("overlay.musicbox.progress", bar, formatTime(elapsedSeconds), formatTime(durationSeconds)).getString();
    }

    private static String formatTime(long seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
