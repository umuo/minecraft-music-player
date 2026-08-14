package com.gitsilence.musicbox.ui.catalog;

import com.gitsilence.musicbox.ui.catalog.provider.KugouCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.provider.NeteaseCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.provider.QqCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.provider.KuwoCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.provider.MiguCatalogProvider;
import java.util.EnumMap;
import java.util.Map;

public final class MusicCatalog {
    private static final Map<MusicPlatform, MusicCatalogProvider> PROVIDERS = createProviders();

    private MusicCatalog() {
    }

    public static MusicCatalogProvider provider(MusicPlatform platform) {
        return PROVIDERS.get(platform);
    }

    private static Map<MusicPlatform, MusicCatalogProvider> createProviders() {
        Map<MusicPlatform, MusicCatalogProvider> providers = new EnumMap<>(MusicPlatform.class);
        register(providers, new KugouCatalogProvider());
        register(providers, new NeteaseCatalogProvider());
        register(providers, new QqCatalogProvider());
        register(providers, new KuwoCatalogProvider());
        register(providers, new MiguCatalogProvider());
        return Map.copyOf(providers);
    }

    private static void register(Map<MusicPlatform, MusicCatalogProvider> providers, MusicCatalogProvider provider) {
        providers.put(provider.platform(), provider);
    }
}
