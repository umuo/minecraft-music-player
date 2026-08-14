package com.gitsilence.musicbox.server.resolver;

import com.gitsilence.musicbox.playback.TrackRef;
import java.util.Map;

public final class ResolverSourceSelector {
    private ResolverSourceSelector() { }

    public static ResolverSourceConfig select(Map<String, ResolverSourceConfig> sources, TrackRef track,
                                               int playerPermissionLevel) {
        ResolverSourceConfig source = sources.get(track.sourceId());
        if (source == null) throw new IllegalArgumentException("Unknown resolver source: " + track.sourceId());
        if (!source.enabled()) throw new IllegalArgumentException("Resolver source is unavailable: " + track.sourceId());
        if (playerPermissionLevel < source.permissionLevel())
            throw new IllegalArgumentException("Resolver source is not permitted: " + track.sourceId());
        if (!source.platforms().contains(track.source()) || !source.qualities().contains(track.quality())
                || !source.capabilities().contains("musicUrl"))
            throw new IllegalArgumentException("Resolver source does not support this track request: " + track.sourceId());
        return source;
    }
}
