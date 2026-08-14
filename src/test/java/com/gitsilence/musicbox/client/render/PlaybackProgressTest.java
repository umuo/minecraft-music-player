package com.gitsilence.musicbox.client.render;

public final class PlaybackProgressTest {
    public static void main(String[] args) {
        PlaybackProgress.State buffering = PlaybackProgress.calculate(90, 100, 20);
        check(buffering.buffering() && buffering.fraction() == 0.0F);
        PlaybackProgress.State halfway = PlaybackProgress.calculate(300, 100, 20);
        check(!halfway.buffering() && Math.abs(halfway.fraction() - 0.5F) < 0.0001F);
        check(PlaybackProgress.calculate(600, 100, 20).fraction() == 1.0F);
        check(!PlaybackProgress.calculate(100, 100, 0).knownDuration());
        System.out.println("Playback progress checks passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
