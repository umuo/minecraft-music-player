package com.gitsilence.musicbox.ui.cover;

public final class CoverTextureCacheTest {
    public static void main(String[] args) {
        check("p1.music.126.net".equals(CoverUrlPolicy.validate("https://p1.music.126.net/a.jpg").getHost()));
        check("y.gtimg.cn".equals(CoverUrlPolicy.validate("https://y.gtimg.cn/a.jpg").getHost()));
        check("img1.kuwo.cn".equals(CoverUrlPolicy.validate("https://img1.kuwo.cn/a.jpg").getHost()));
        check("d.musicapp.migu.cn".equals(CoverUrlPolicy.validate("https://d.musicapp.migu.cn/a.webp").getHost()));
        rejects("http://p1.music.126.net/a.jpg");
        rejects("https://music.126.net.example.org/a.jpg");
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};
        check(CoverImagePolicy.isPngResponse("image/png; charset=binary", png));
        check(!CoverImagePolicy.isPngResponse("text/html", png));
        check(!CoverImagePolicy.isPngResponse("image/png", "not a png".getBytes()));
        System.out.println("Cover URL policy checks passed");
    }
    private static void rejects(String value) {
        try { CoverUrlPolicy.validate(value); throw new AssertionError("accepted " + value); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
