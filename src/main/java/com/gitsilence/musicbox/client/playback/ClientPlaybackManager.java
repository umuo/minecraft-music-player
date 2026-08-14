package com.gitsilence.musicbox.client.playback;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.client.lyrics.ClientLyricsManager;
import com.gitsilence.musicbox.network.payload.StartTrackPayload;
import com.gitsilence.musicbox.playback.TrackRef;
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
    private static volatile PlaybackSnapshot snapshot;

    public record PlaybackSnapshot(BlockPos pos, TrackRef track, long startGameTime, int broadcastRadius) { }

    private ClientPlaybackManager() {
    }

    public static void start(StartTrackPayload payload) {
        stopCurrent();
        sourcePos = payload.pos();
        activeRadius = payload.broadcastRadius();
        snapshot = new PlaybackSnapshot(payload.pos(), payload.track(), payload.startGameTime(), payload.broadcastRadius());
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

    /** Immutable client-side broadcast state for renderers. Never contains the audio or lyric URL. */
    public static PlaybackSnapshot snapshot() {
        return snapshot;
    }

    private static void playResolved(StartTrackPayload payload, long generation, long startAtNanos) {
        try {
            URI uri = ResolverSecurityPolicy.validateClientAudio(payload.audioUrl());
            if (GENERATION.get() != generation) {
                return;
            }
            HttpResponse<InputStream> response = openAudio(uri);
            try (java.io.BufferedInputStream stream = new java.io.BufferedInputStream(response.body(), 8192)) {
                stream.mark(256);
                byte[] header = new byte[256];
                int read = stream.read(header);
                stream.reset();
                
                if (read > 0) {
                    if (header[0] == '{' || header[0] == '<') {
                        String text = new String(header, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim();
                        throw new IllegalStateException("CDN returned error payload: " + text.substring(0, Math.min(text.length(), 100)));
                    }
                    if (read >= 4) {
                        String magic = new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
                        if (magic.startsWith("fLaC")) {
                            throw new IllegalStateException("Received FLAC audio. Currently only MP3 is supported by the client decoder.");
                        }
                        if (magic.substring(4).startsWith("ftyp") || magic.contains("ftypM4A") || magic.contains("ftypmp42")) {
                            throw new IllegalStateException("Received M4A/MP4 audio. Currently only MP3 is supported by the client decoder.");
                        }
                    }
                }

                PositionalMp3Player nextPlayer = new PositionalMp3Player(stream);
                if (GENERATION.get() != generation) { nextPlayer.close(); return; }
                waitUntil(startAtNanos, generation);
                if (GENERATION.get() != generation) { nextPlayer.close(); return; }
                player = nextPlayer;
                MusicBoxMod.LOGGER.info("Starting Java Sound playback for '{}' at {}", payload.track().title(), payload.pos());
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
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(60)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close(); throw new IllegalStateException("Audio CDN returned HTTP " + response.statusCode());
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
        snapshot = null;
        if (stoppedPos != null) ClientLyricsManager.stop(stoppedPos);
        if (current != null) {
            current.close();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (player != null && sourcePos != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (mc.level.isLoaded(sourcePos)) {
                    if (!(mc.level.getBlockEntity(sourcePos) instanceof com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity)) {
                        stopCurrent();
                        return;
                    }
                }
                double distSq = mc.player.distanceToSqr(Vec3.atCenterOf(sourcePos));
                double maxDist = activeRadius;
                float volume = distSq >= maxDist * maxDist ? 0f : (float) (1.0 - Math.sqrt(distSq) / maxDist);
                player.setVolume(volume);
            }
        }
    }
}
