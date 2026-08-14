package com.gitsilence.musicbox.ui.cover;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class CoverTextureCache implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, ResourceLocation> textures = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> unavailable = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public ResourceLocation get(String url) {
        if (url == null || url.isBlank() || closed || unavailable.contains(url)) return null;
        ResourceLocation texture = textures.get(url);
        if (texture == null && pending.add(url)) download(url);
        return texture;
    }

    private void download(String value) {
        URI uri;
        try { uri = CoverUrlPolicy.validate(value); }
        catch (RuntimeException error) {
            pending.remove(value);
            markUnavailable(value, "URL was rejected");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "image/png,image/jpeg,image/*;q=0.8").GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body().length == 0 || response.body().length > MAX_BYTES) {
                markUnavailable(value, "HTTP " + response.statusCode() + ", " + response.body().length + " bytes");
                return;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!CoverImagePolicy.isPngResponse(contentType, response.body())) {
                markUnavailable(value, "response is not a PNG");
                return;
            }
            try (NativeImage image = NativeImage.read(new ByteArrayInputStream(response.body()))) {
                if (image.getWidth() < 1 || image.getHeight() < 1
                        || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                    markUnavailable(value, "image dimensions are unsupported");
                    return;
                }
                NativeImage owned = new NativeImage(image.format(), image.getWidth(), image.getHeight(), false);
                owned.copyFrom(image);
                Minecraft.getInstance().execute(() -> register(value, owned));
            } catch (Exception error) {
                markUnavailable(value, "PNG could not be decoded");
            }
        }).whenComplete((unused, error) -> {
            pending.remove(value);
            if (error != null) markUnavailable(value, "download failed");
        });
    }

    private void markUnavailable(String url, String reason) {
        if (unavailable.add(url)) LOGGER.debug("Music cover unavailable ({}): {}", reason, url);
    }

    private void register(String url, NativeImage image) {
        if (closed) { image.close(); return; }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("musicbox", "cover/" + Integer.toHexString(url.hashCode()));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        textures.put(url, id);
        LOGGER.debug("Registered music cover {} as {}", url, id);
    }

    @Override public void close() {
        closed = true;
        textures.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        textures.clear();
        pending.clear();
        unavailable.clear();
    }
}
