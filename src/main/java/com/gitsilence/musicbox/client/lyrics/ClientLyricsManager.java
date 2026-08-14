package com.gitsilence.musicbox.client.lyrics;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.client.api.LxPlaybackResolver;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = MusicBoxMod.MOD_ID, value = Dist.CLIENT)
public final class ClientLyricsManager {
    private static volatile LyricTimeline timeline = LyricTimeline.parse("");
    private static volatile BlockPos source;
    private static volatile long startGameTime;
    private static volatile long generation;
    private ClientLyricsManager() { }

    public static void start(StartTrackPayload payload) {
        long request = ++generation;
        source = payload.pos();
        startGameTime = payload.startGameTime();
        timeline = LyricTimeline.parse("");
        new LxPlaybackResolver().resolveLyrics(payload.track()).thenAccept(lrc -> {
            if (generation == request) timeline = LyricTimeline.parse(lrc);
        }).exceptionally(error -> null);
    }

    public static void stop(BlockPos pos) {
        if (pos.equals(source)) { generation++; source = null; timeline = LyricTimeline.parse(""); }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (source == null || minecraft.level == null || minecraft.options.hideGui) return;
        long elapsed = Math.max(0, minecraft.level.getGameTime() - startGameTime) * 50L;
        String line = timeline.at(elapsed);
        if (line.isEmpty()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        String clipped = minecraft.font.plainSubstrByWidth(line, graphics.guiWidth() - 40);
        int x = (graphics.guiWidth() - minecraft.font.width(clipped)) / 2;
        int y = graphics.guiHeight() - 58;
        graphics.fill(x - 5, y - 3, x + minecraft.font.width(clipped) + 5, y + 12, 0x90000000);
        graphics.drawString(minecraft.font, clipped, x, y, 0xFFF0F1F4, false);
    }
}
