package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.*;
import com.google.gson.*;
import java.util.List;

public final class AdditionalProvidersTest {
    public static void main(String[] args) {
        platforms(); qq(); kuwo(); migu();
        System.out.println("QQ/Kuwo/Migu provider parsing checks passed");
    }
    private static void platforms() {
        check(MusicPlatform.QQ.source().equals("tx"));
        check(MusicPlatform.KUWO.source().equals("kw"));
        check(MusicPlatform.MIGU.source().equals("mg"));
        for (MusicPlatform platform : MusicPlatform.values()) check(MusicCatalog.provider(platform) != null);
    }
    private static void qq() {
        JsonArray values = array("""
                [{"songmid":"mid1","songname":"Song","interval":123,"albummid":"album1","albumname":"Album","size128":1,"size320":2,"sizeflac":3,"singer":[{"name":"Singer"}]}]
                """);
        CatalogTrack track = QqCatalogProvider.parseTracks(values).getFirst();
        check(track.platform() == MusicPlatform.QQ && track.id().equals("mid1") && track.durationSeconds() == 123);
        check(track.coverUrl().contains("album1") && track.hasQuality("128k") && track.hasQuality("320k"));
        check(track.hasQuality("flac") && !track.hasQuality("flac24bit"));
    }
    private static void kuwo() {
        JsonArray values = array("""
                [{"MUSICRID":"MUSIC_42","SONGNAME":"Song","ARTIST":"Singer","ALBUM":"Album","DURATION":"234","MINFO":"level:h,bitrate:128,format:mp3;level:p,bitrate:320,format:mp3;level:ff,bitrate:2000,format:flac"}]
                """);
        CatalogTrack track = KuwoCatalogProvider.parseTracks(values).getFirst();
        check(track.platform() == MusicPlatform.KUWO && track.id().equals("MUSIC_42"));
        check(track.hasQuality("128k") && track.hasQuality("320k") && track.hasQuality("flac") && !track.hasQuality("flac24bit"));
    }
    private static void migu() {
        JsonArray values = array("""
                [{"contentId":"content","copyrightId":"copyright","name":"Song","singers":[{"name":"Singer"}],"albums":[{"name":"Album"}],"imgItems":[{"img":"https://d.musicapp.migu.cn/a.webp"}],"newRateFormats":[{"formatType":"PQ"},{"formatType":"HQ"},{"formatType":"SQ"},{"formatType":"ZQ"}]}]
                """);
        CatalogTrack track = MiguCatalogProvider.parseTracks(values).getFirst();
        check(track.platform() == MusicPlatform.MIGU && track.id().equals("copyright"));
        check(track.hasQuality("128k") && track.hasQuality("320k") && track.hasQuality("flac") && track.hasQuality("flac24bit"));
    }
    private static JsonArray array(String json) { return JsonParser.parseString(json).getAsJsonArray(); }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
