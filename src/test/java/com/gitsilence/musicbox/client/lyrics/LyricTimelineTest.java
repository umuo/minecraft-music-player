package com.gitsilence.musicbox.client.lyrics;

public final class LyricTimelineTest {
    public static void main(String[] args) {
        LyricTimeline timeline = LyricTimeline.parse("[00:01.50]first\n[00:03.005][00:04.00]second");
        check(timeline.lines().size() == 3);
        check(timeline.at(1499).isEmpty());
        check("first".equals(timeline.at(1500)));
        check("second".equals(timeline.at(4000)));
        System.out.println("LRC timeline checks passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
