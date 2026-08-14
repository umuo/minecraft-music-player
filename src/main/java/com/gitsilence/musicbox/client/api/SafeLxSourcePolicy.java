package com.gitsilence.musicbox.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Defines the only source format accepted by the client: a declarative pointer to an HTTP bridge. */
public final class SafeLxSourcePolicy {
    public record Descriptor(String name, String resolverUrl) { }
    private SafeLxSourcePolicy() { }

    public static Descriptor parseDescriptor(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Source descriptor is empty");
        String stripped = text.strip();
        if (!stripped.startsWith("{")) {
            throw new IllegalArgumentException("JavaScript LX sources are not executed; configure a safe HTTP bridge");
        }
        JsonObject json;
        try { json = JsonParser.parseString(stripped).getAsJsonObject(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Invalid safe source descriptor", error); }
        if (!"musicbox-safe-source-v1".equals(string(json, "format"))) {
            throw new IllegalArgumentException("Unsupported source format");
        }
        String name = string(json, "name").strip();
        String resolver = string(json, "resolverUrl").strip();
        if (name.isEmpty() || resolver.isEmpty()) throw new IllegalArgumentException("Descriptor requires name and resolverUrl");
        return new Descriptor(name, resolver);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
