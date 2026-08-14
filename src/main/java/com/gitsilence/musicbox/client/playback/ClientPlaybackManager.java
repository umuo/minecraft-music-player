package com.gitsilence.musicbox.client.playback;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.client.api.LxPlaybackResolver;
import com.gitsilence.musicbox.client.lyrics.ClientLyricsManager;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javazoom.jl.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = MusicBoxMod.MOD_ID, value = Dist.CLIENT)
public final class ClientPlaybackManager {
    private static final ExecutorService PLAYBACK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicbox-playback");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong GENERATION = new AtomicLong();

    private static volatile Player player;
    private static volatile BlockPos sourcePos;

    private ClientPlaybackManager() {
    }

    public static void start(StartTrackPayload payload) {
        stopCurrent();
        sourcePos = payload.pos();
        long generation = GENERATION.incrementAndGet();
        Minecraft minecraft = Minecraft.getInstance();
        long currentGameTime = minecraft.level == null ? payload.startGameTime() : minecraft.level.getGameTime();
        long waitMillis = Math.max(0, payload.startGameTime() - currentGameTime) * 50L;
        long startAtNanos = System.nanoTime() + waitMillis * 1_000_000L;

        PLAYBACK_EXECUTOR.execute(() -> playResolved(payload, generation, startAtNanos));
    }

    public static void stop(BlockPos pos) {
        if (pos.equals(sourcePos)) {
            stopCurrent();
        }
    }

    private static void playResolved(StartTrackPayload payload, long generation, long startAtNanos) {
        try {
            LxPlaybackResolver resolver = new LxPlaybackResolver();
            URI uri = resolver.resolve(payload.track()).join();
            if (GENERATION.get() != generation) {
                return;
            }
            HttpResponse<InputStream> response = resolver.openAudio(uri);
            try (InputStream stream = response.body()) {
                waitUntil(startAtNanos, generation);
                if (GENERATION.get() != generation) {
                    return;
                }
                Player nextPlayer = new Player(stream);
                player = nextPlayer;
                nextPlayer.play();
            }
        } catch (Exception error) {
            MusicBoxMod.LOGGER.warn("Unable to play {}", payload.track().title(), error);
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null && GENERATION.get() == generation) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("message.musicbox.playback_failed", rootMessage(error)),
                            false
                    );
                }
            });
        } finally {
            if (GENERATION.get() == generation) {
                ClientLyricsManager.stop(payload.pos());
                player = null;
                sourcePos = null;
            }
        }
    }

    private static void waitUntil(long startAtNanos, long generation) throws InterruptedException {
        while (GENERATION.get() == generation) {
            long remaining = startAtNanos - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            Thread.sleep(Math.min(remaining / 1_000_000L, 50L));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private static void stopCurrent() {
        GENERATION.incrementAndGet();
        Player current = player;
        player = null;
        BlockPos stoppedPos = sourcePos;
        sourcePos = null;
        if (stoppedPos != null) ClientLyricsManager.stop(stoppedPos);
        if (current != null) {
            current.close();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos activePos = sourcePos;
        if (activePos == null || minecraft.player == null) {
            return;
        }
        double radiusSquared = Math.pow(MusicBoxConfig.BROADCAST_RADIUS.getAsInt(), 2);
        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(activePos)) > radiusSquared) {
            stopCurrent();
        }
    }
}
