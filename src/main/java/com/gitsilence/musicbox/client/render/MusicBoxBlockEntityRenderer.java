package com.gitsilence.musicbox.client.render;

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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class MusicBoxBlockEntityRenderer implements BlockEntityRenderer<MusicBoxBlockEntity> {
    private static final int MAX_TEXT_WIDTH = 180;
    private final BlockEntityRendererProvider.Context context;
    private static boolean hasLogged = false;

    public MusicBoxBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(MusicBoxBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ClientPlaybackManager.PlaybackSnapshot state = ClientPlaybackManager.snapshot();
        TrackRef track = (state != null && entity.getBlockPos().equals(state.pos())) ? state.track() : entity.currentTrack();
        if (track == null || !track.isValid()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        double distance = camera.getPosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(entity.getBlockPos()));
        double limit = state != null ? Math.max(32.0, (double) state.broadcastRadius()) : 32.0;
        if (distance > limit) {
            return;
        }
        
        if (!hasLogged) {
            hasLogged = true;
            com.gitsilence.musicbox.MusicBoxMod.LOGGER.info("MusicBoxBlockEntityRenderer is RUNNING! Track: {}, Distance: {}", track.title(), distance);
        }

        poseStack.pushPose();
        // Move to top center of the music box
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Font font = minecraft.font;
        Matrix4f matrix = poseStack.last().pose();
        List<String> lines = new ArrayList<>();
        lines.add(track.artist().isBlank() ? track.title()
                : Component.translatable("overlay.musicbox.title_artist", track.title(), track.artist()).getString());


        long gameTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        long startTime = (state != null && entity.getBlockPos().equals(state.pos())) ? state.startGameTime() : entity.startGameTime();
        PlaybackProgress.State progress = PlaybackProgress.calculate(gameTime, startTime, track.durationSeconds());
        lines.add(progressLine(progress));

        ClientLyricsManager.LyricsSnapshot lyrics = ClientLyricsManager.snapshot(gameTime);
        if (lyrics != null && lyrics.source().equals(entity.getBlockPos())) {
            lines.addAll(lyrics.lines());
        }

        int y = 0;
        float backgroundOpacity = minecraft.options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (backgroundOpacity * 255.0F) << 24;
        
        for (int index = 0; index < lines.size(); index++) {
            String clipped = font.plainSubstrByWidth(lines.get(index), MAX_TEXT_WIDTH);
            float x = -font.width(clipped) / 2.0F;
            int textColor = index == 0 ? 0xFFF2C14E : index == 1 ? 0xFFD8DEE9 : 0xFFF0F1F4;
            
            // Background and see-through text
            font.drawInBatch(clipped, x, y, 0x20FFFFFF, false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, backgroundColor, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
            
            // Normal foreground text
            font.drawInBatch(clipped, x, y, textColor, false, matrix, bufferSource,
                    Font.DisplayMode.NORMAL, 0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
            
            y += index == 1 ? 13 : 11;
        }

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 4096;
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
