package com.gitsilence.musicbox.server.playlist;

import com.gitsilence.musicbox.ui.catalog.CatalogPlaylist;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import java.util.List;

public record SharedPlaylist(String key, String platform, String playlistId, String canonicalUrl, String name,
                             String creator, String coverUrl, long importedAtEpochMillis, List<SharedTrack> tracks) {
    public SharedPlaylist {
        key=safe(key,160);platform=safe(platform,16);playlistId=safe(playlistId,128);canonicalUrl=safeHttps(canonicalUrl);
        name=safe(name,128);creator=safe(creator,128);coverUrl=safeHttps(coverUrl);importedAtEpochMillis=Math.max(0,importedAtEpochMillis);
        tracks=tracks==null?List.of():List.copyOf(tracks);
    }
    public MusicPlatform musicPlatform(){return MusicPlatform.fromSource(platform);}
    public CatalogPlaylist toCatalogPlaylist(){return new CatalogPlaylist(musicPlatform(),playlistId,name,creator,coverUrl,tracks.size(),0,"");}
    private static String safe(String value,int max){String text=value==null?"":value.strip();return text.length()<=max?text:text.substring(0,max);}
    private static String safeHttps(String value){String text=safe(value,512);return text.startsWith("https://")?text:"";}
}
