package com.gitsilence.musicbox.ui.catalog.provider;

import com.gitsilence.musicbox.ui.catalog.CatalogException;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class CatalogHttp {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    CompletableFuture<JsonElement> getJson(String baseUrl, Map<String, String> parameters) {
        return getText(uri(baseUrl, parameters)).thenApply(body -> {
            try {
                return JsonParser.parseString(body);
            } catch (RuntimeException error) {
                throw new CatalogException("Invalid JSON returned by music platform", error);
            }
        });
    }

    CompletableFuture<String> getText(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json,text/html;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 MusicBox/0.2")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CatalogException("Music platform returned HTTP " + response.statusCode());
                    }
                    return response.body();
                });
    }

    static Map<String, String> params(Object... entries) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put(String.valueOf(entries[i]), String.valueOf(entries[i + 1]));
        }
        return values;
    }

    static URI uri(String baseUrl, Map<String, String> parameters) {
        StringBuilder value = new StringBuilder(baseUrl);
        value.append(baseUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) value.append('&');
            first = false;
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return URI.create(value.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
