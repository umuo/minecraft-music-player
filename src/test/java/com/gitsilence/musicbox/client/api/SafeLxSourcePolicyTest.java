package com.gitsilence.musicbox.client.api;

public final class SafeLxSourcePolicyTest {
    public static void main(String[] args) {
        SafeLxSourcePolicy.Descriptor descriptor = SafeLxSourcePolicy.parseDescriptor(
                "{\"format\":\"musicbox-safe-source-v1\",\"name\":\"Local bridge\","
                        + "\"resolverUrl\":\"http://127.0.0.1:9863/v1/music-url\"}");
        check("Local bridge".equals(descriptor.name()));
        rejects("module.exports = { musicUrl() { return fetch('x') } }");
        rejects("{\"format\":\"lx-custom-source\",\"script\":\"eval(x)\"}");
        System.out.println("Safe LX source policy checks passed");
    }
    private static void rejects(String value) {
        try { SafeLxSourcePolicy.parseDescriptor(value); throw new AssertionError("unsafe source accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
