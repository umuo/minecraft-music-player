package com.gitsilence.musicbox.network.payload;

import com.gitsilence.musicbox.playback.TrackRef;
import com.gitsilence.musicbox.server.resolver.ResolverSourceConfig;
import com.gitsilence.musicbox.server.resolver.ResolverSourceRegistry;
import com.gitsilence.musicbox.server.resolver.ResolverSourceSelector;
import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class ResolverSourcesTest {
    public static void main(String[] args) {
        parsesAndRedacts(); legacyDefault(); networkRoundTrip(); selection();
        System.out.println("Named resolver source checks passed");
    }

    private static void parsesAndRedacts() {
        String json = """
                {"id":"local-b","displayName":"Bridge B","playbackApiUrl":"http://127.0.0.1:9863/sources/local-b/v1/music-url",
                 "token":"top-secret","allowedAudioHosts":["cdn.example"],"requireHttps":true,"timeoutSeconds":12,
                 "platforms":["kg","wy"],"qualities":["128k","320k"],"capabilities":["musicUrl","lyric"]}
                """;
        ResolverSourceConfig source = ResolverSourceRegistry.parse(List.of(json), null).get("local-b");
        check(source.token().equals("top-secret") && source.timeoutSeconds() == 12, "server config not parsed");
        String defaults = json.replace("\"timeoutSeconds\":12,", "");
        check(ResolverSourceRegistry.parse(List.of(defaults), null).get("local-b").timeoutSeconds() == 60,
                "named resolver timeout default is not 60 seconds");
        check(source.playbackApiUrl().endsWith("/sources/local-b/v1/music-url"), "resolver path was not preserved");
        String publicJson = new Gson().toJson(source.publicInfo());
        check(publicJson.contains("local-b") && publicJson.contains("Bridge B"), "public source is incomplete");
        check(!publicJson.contains("top-secret") && !publicJson.contains("9863") && !publicJson.contains("/sources/")
                && !publicJson.contains("playbackApiUrl") && !publicJson.contains("allowedAudioHosts"),
                "public source leaked private resolver configuration");
    }

    private static void legacyDefault() {
        ResolverSourceConfig legacy = config("default", "http://127.0.0.1:9863/v1/music-url", 0);
        Map<String, ResolverSourceConfig> parsed = ResolverSourceRegistry.parse(List.of(), legacy);
        check(parsed.size() == 1 && parsed.get("default") == legacy, "legacy resolver was not mapped to default");
    }

    private static void networkRoundTrip() {
        TrackRef original = track("bridge-b");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PayloadCodecs.writeTrack(buffer, original);
        TrackRef decoded = PayloadCodecs.readTrack(buffer);
        check(decoded.equals(original) && decoded.sourceId().equals("bridge-b"), "track sourceId did not round-trip");

        OpenMusicBoxPayload open = new OpenMusicBoxPayload(new BlockPos(1, 2, 3),
                List.of(config("bridge-b", "http://127.0.0.1:9864/v1/music-url", 0).publicInfo()));
        RegistryFriendlyByteBuf openBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        OpenMusicBoxPayload.STREAM_CODEC.encode(openBuffer, open);
        OpenMusicBoxPayload decodedOpen = OpenMusicBoxPayload.STREAM_CODEC.decode(openBuffer);
        check(decodedOpen.equals(open), "public resolver catalog did not round-trip");
    }

    private static void selection() {
        ResolverSourceConfig source = config("bridge-b", "http://127.0.0.1:9864/v1/music-url", 2);
        Map<String, ResolverSourceConfig> sources = Map.of(source.id(), source);
        check(ResolverSourceSelector.select(sources, track("bridge-b"), 2) == source, "resolver not selected");
        rejects(() -> ResolverSourceSelector.select(sources, track("unknown"), 4));
        rejects(() -> ResolverSourceSelector.select(sources, track("bridge-b"), 1));
    }

    private static ResolverSourceConfig config(String id, String url, int permission) {
        return new ResolverSourceConfig(id, "Bridge", url, "secret", List.of("cdn.example"), true, 15,
                List.of("kg"), List.of("320k"), List.of("musicUrl", "lyric"), true, permission);
    }

    private static TrackRef track(String sourceId) {
        return new TrackRef("kg", "42", "Song", "Artist", "Album", 120,
                "h128", "h320", "", "", "320k", sourceId);
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("invalid resolver source accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
