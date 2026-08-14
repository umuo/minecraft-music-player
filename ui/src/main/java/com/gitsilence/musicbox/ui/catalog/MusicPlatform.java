package com.gitsilence.musicbox.ui.catalog;

public enum MusicPlatform {
    KUGOU("kg", "screen.musicbox.platform.kugou"),
    NETEASE("wy", "screen.musicbox.platform.netease"),
    QQ("tx", "screen.musicbox.platform.qq"),
    KUWO("kw", "screen.musicbox.platform.kuwo"),
    MIGU("mg", "screen.musicbox.platform.migu");

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

    public static MusicPlatform fromSource(String source) {
        if (source == null) throw new IllegalArgumentException("Unknown music platform");
        for (MusicPlatform platform : values()) {
            if (platform.source.equalsIgnoreCase(source) || platform.name().equalsIgnoreCase(source)) return platform;
        }
        throw new IllegalArgumentException("Unknown music platform");
    }
}
