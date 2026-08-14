package com.gitsilence.musicbox.ui.cover;

public final class CoverTextureCacheTest {
    public static void main(String[] args) {
        check("p1.music.126.net".equals(CoverUrlPolicy.validate("https://p1.music.126.net/a.jpg").getHost()));
        rejects("http://p1.music.126.net/a.jpg");
        rejects("https://music.126.net.example.org/a.jpg");
        System.out.println("Cover URL policy checks passed");
    }
    private static void rejects(String value) {
        try { CoverUrlPolicy.validate(value); throw new AssertionError("accepted " + value); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
