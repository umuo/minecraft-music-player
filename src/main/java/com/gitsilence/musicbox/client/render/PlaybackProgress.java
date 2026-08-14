package com.gitsilence.musicbox.client.render;

public final class PlaybackProgress {
    private PlaybackProgress() { }

    public record State(long elapsedTicks, long durationTicks, float fraction, boolean buffering, boolean knownDuration) { }

    public static State calculate(long gameTime, long startGameTime, int durationSeconds) {
        long elapsed = Math.max(0L, gameTime - startGameTime);
        boolean buffering = gameTime < startGameTime;
        long duration = durationSeconds > 0 ? durationSeconds * 20L : 0L;
        float fraction = duration == 0 ? 0.0F : Math.min(1.0F, (float) elapsed / duration);
        return new State(elapsed, duration, fraction, buffering, duration > 0);
    }
}
