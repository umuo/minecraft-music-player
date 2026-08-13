package com.gitsilence.musicbox.ui.catalog.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class JsonSupport {
    private JsonSupport() {
    }

    static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static int integer(JsonObject object, String name) {
        return (int) Math.min(Integer.MAX_VALUE, number(object, name));
    }

    static long number(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0;
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    static String joinedNames(JsonArray array, String field) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            String name = string(element.getAsJsonObject(), field);
            if (!name.isBlank()) names.add(name);
        }
        return String.join("、", names);
    }

    static String htmlDecode(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
