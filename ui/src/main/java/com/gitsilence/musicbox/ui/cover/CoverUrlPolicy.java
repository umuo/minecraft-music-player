package com.gitsilence.musicbox.ui.cover;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class CoverUrlPolicy {
    private static final Set<String> HOST_SUFFIXES = Set.of(
            "kugou.com", "kugoucdn.com", "music.126.net", "qpic.cn", "qq.com", "gtimg.cn",
            "kuwo.cn", "migu.cn", "musicapp.migu.cn"
    );
    private CoverUrlPolicy() { }
    static URI validate(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Cover URL must use HTTPS");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (HOST_SUFFIXES.stream().noneMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed))) {
            throw new IllegalArgumentException("Cover host is not allowed");
        }
        return uri;
    }
}
