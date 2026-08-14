package com.gitsilence.musicbox.client.playback;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.client.lyrics.ClientLyricsManager;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.server.resolver.ResolverSecurityPolicy;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
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

    private static volatile PositionalMp3Player player;
    private static volatile BlockPos sourcePos;
    private static volatile int activeRadius;

    private ClientPlaybackManager() {
    }

    public static void start(StartTrackPayload payload) {
        stopCurrent();
        sourcePos = payload.pos();
        activeRadius = payload.broadcastRadius();
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
            URI uri = ResolverSecurityPolicy.validateClientAudio(payload.audioUrl());
            if (GENERATION.get() != generation) {
                return;
            }
            HttpResponse<InputStream> response = openAudio(uri);
            try (InputStream stream = response.body()) {
                PositionalMp3Player nextPlayer = new PositionalMp3Player(stream);
                if (GENERATION.get() != generation) { nextPlayer.close(); return; }
                waitUntil(startAtNanos, generation);
                if (GENERATION.get() != generation) { nextPlayer.close(); return; }
                player = nextPlayer;
                nextPlayer.play(Vec3.atCenterOf(payload.pos()), payload.broadcastRadius());
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

    private static HttpResponse<InputStream> openAudio(URI uri) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "audio/mpeg,audio/*;q=0.9,*/*;q=0.1").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close(); throw new IllegalStateException("Audio CDN returned HTTP " + response.statusCode());
        }
        String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.startsWith("audio/") && !type.startsWith("application/octet-stream")) {
            response.body().close(); throw new IllegalStateException("Audio CDN returned unsupported Content-Type");
        }
        return response;
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
        PositionalMp3Player current = player;
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
        double radiusSquared = Math.pow(activeRadius, 2);
        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(activePos)) > radiusSquared) {
            stopCurrent();
        }
    }
}
