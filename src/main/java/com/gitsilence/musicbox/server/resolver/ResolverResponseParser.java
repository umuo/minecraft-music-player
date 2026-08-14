package com.gitsilence.musicbox.server.resolver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ResolverResponseParser {
    private ResolverResponseParser() { }

    public static String audioUrl(int status, String body) {
        requireSuccess(status);
        JsonElement json = JsonParser.parseString(body);
        String url = json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() ? json.getAsString() : null;
        if (json.isJsonObject()) {
            url = member(json.getAsJsonObject(), "url");
            if (url == null && json.getAsJsonObject().has("data") && json.getAsJsonObject().get("data").isJsonObject())
                url = member(json.getAsJsonObject().getAsJsonObject("data"), "url");
        }
        if (url == null || url.isBlank()) throw new IllegalStateException("Playback API response does not contain a URL");
        return url;
    }

    public static String lyrics(int status, String body) {
        requireSuccess(status);
        JsonElement json = JsonParser.parseString(body);
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) return json.getAsString();
        if (!json.isJsonObject()) return "";
        JsonObject object = json.getAsJsonObject();
        String lyric = member(object, "lyric");
        if (lyric == null) lyric = member(object, "lrc");
        if (lyric == null && object.has("data") && object.get("data").isJsonObject()) {
            lyric = member(object.getAsJsonObject("data"), "lyric");
            if (lyric == null) lyric = member(object.getAsJsonObject("data"), "lrc");
        }
        return lyric == null ? "" : lyric;
    }

    private static void requireSuccess(int status) {
        if (status < 200 || status >= 300) throw new IllegalStateException("Playback API returned HTTP " + status);
    }
    private static String member(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
