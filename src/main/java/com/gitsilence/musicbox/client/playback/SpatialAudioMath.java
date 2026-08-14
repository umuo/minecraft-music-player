package com.gitsilence.musicbox.client.playback;

public final class SpatialAudioMath {
    private SpatialAudioMath() { }
    public static float linearGain(double distance, double radius) {
        if (radius <= 0) return 0;
        return (float) Math.max(0, Math.min(1, 1 - distance / radius));
    }
}
