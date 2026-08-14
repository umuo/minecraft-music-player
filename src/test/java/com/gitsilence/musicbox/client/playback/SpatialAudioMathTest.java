package com.gitsilence.musicbox.client.playback;

public final class SpatialAudioMathTest {
    public static void main(String[] args) {
        check(SpatialAudioMath.linearGain(0, 64) == 1F);
        check(SpatialAudioMath.linearGain(32, 64) == 0.5F);
        check(SpatialAudioMath.linearGain(80, 64) == 0F);
        System.out.println("Positional attenuation checks passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
