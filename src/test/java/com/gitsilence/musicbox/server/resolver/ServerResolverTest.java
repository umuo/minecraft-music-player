package com.gitsilence.musicbox.server.resolver;

import com.gitsilence.musicbox.config.ResolverConfigKeys;
import java.net.URI;
import java.util.List;

public final class ServerResolverTest {
    public static void main(String[] args) {
        urls(); responses(); requestSources(); configSeparation();
        System.out.println("Server resolver checks passed");
    }
    private static void urls() {
        URI uri = ResolverSecurityPolicy.validateAudio("https://media.cdn.example/song.mp3",
                List.of("cdn.example"), true);
        check("media.cdn.example".equals(uri.getHost()), "allowed subdomain rejected");
        rejects(() -> ResolverSecurityPolicy.validateAudio("https://cdn.example.evil/song.mp3", List.of("cdn.example"), true));
        rejects(() -> ResolverSecurityPolicy.validateAudio("http://cdn.example/song.mp3", List.of("cdn.example"), true));
        rejects(() -> ResolverSecurityPolicy.validateAudio("https://user@cdn.example/song.mp3", List.of("cdn.example"), true));
        ResolverSecurityPolicy.validateEndpoint("http://127.0.0.1:9863/resolve", true);
    }
    private static void responses() {
        check("https://cdn.example/a.mp3".equals(ResolverResponseParser.audioUrl(200,
                "{\"data\":{\"url\":\"https://cdn.example/a.mp3\"}}")), "nested URL not parsed");
        check("[00:01]line".equals(ResolverResponseParser.lyrics(200,
                "{\"data\":{\"lrc\":\"[00:01]line\"}}")), "nested lyrics not parsed");
        rejects(() -> ResolverResponseParser.audioUrl(302, "{}"));
        rejects(() -> ResolverResponseParser.audioUrl(200, "{}"));
    }
    private static void configSeparation() {
        check(ResolverConfigKeys.SERVER.contains("playbackApiToken"), "token missing from server config");
        check(!ResolverConfigKeys.CLIENT.contains("playbackApiToken"), "token exposed in client config");
        check(!ResolverConfigKeys.CLIENT.contains("playbackApiUrl"), "resolver URL exposed in client config");
    }
    private static void requestSources() {
        for (String source : List.of("tx", "kw", "mg")) {
            var track = new com.gitsilence.musicbox.playback.TrackRef(source, "platform-id", "Song", "Artist", "Album",
                    120, "h128", "h320", "flac", "hires", "320k", "bridge");
            var json = ServerPlaybackResolver.requestJson(track, "musicUrl");
            check(source.equals(json.get("source").getAsString()), source + " resolver source changed");
            var info = json.getAsJsonObject("info").getAsJsonObject("musicInfo");
            check("platform-id".equals(info.get("songmid").getAsString()), source + " platform id missing from resolver request");
            String alias = switch (source) { case "kw" -> "musicrid"; case "mg" -> "copyrightId"; default -> "mid"; };
            check("platform-id".equals(info.get(alias).getAsString()), source + " LX id alias missing from resolver request");
        }
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("unsafe input accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
