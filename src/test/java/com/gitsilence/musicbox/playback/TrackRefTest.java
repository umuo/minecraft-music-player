package com.gitsilence.musicbox.playback;

final class TrackRefTest {
    public static void main(String[] args) {
        validatesAndFallsBackAcrossQualities();
        rejectsUnknownSourcesAndBoundsInput();
        System.out.println("TrackRef checks passed");
    }

    static void validatesAndFallsBackAcrossQualities() {
        TrackRef track = new TrackRef("kg", "42", "Song", "Artist", "Album", 120,
                "low", "high", "", "", "flac");
        check(track.isValid(), "valid track rejected");
        check("high".equals(track.hashForQuality("flac")), "quality fallback failed");
    }

    static void rejectsUnknownSourcesAndBoundsInput() {
        TrackRef track = new TrackRef("evil", "id", "x".repeat(200), "", "", -1,
                "", "", "", "", "128k");
        check(!track.isValid(), "unknown source accepted");
        check(track.title().length() == 128, "title was not bounded");
        check(track.durationSeconds() == 0, "negative duration was not bounded");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
