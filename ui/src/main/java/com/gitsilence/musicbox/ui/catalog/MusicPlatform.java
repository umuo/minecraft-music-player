package com.gitsilence.musicbox.ui.catalog;

public enum MusicPlatform {
    KUGOU("kg", "screen.musicbox.platform.kugou"),
    NETEASE("wy", "screen.musicbox.platform.netease");

    private final String source;
    private final String translationKey;

    MusicPlatform(String source, String translationKey) {
        this.source = source;
        this.translationKey = translationKey;
    }

    public String source() {
        return source;
    }

    public String translationKey() {
        return translationKey;
    }
}
