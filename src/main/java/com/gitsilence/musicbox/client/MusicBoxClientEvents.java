package com.gitsilence.musicbox.client;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.client.render.MusicBoxBlockEntityRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class MusicBoxClientEvents {
    private MusicBoxClientEvents() { }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MusicBoxMod.MUSIC_BOX_ENTITY.get(), MusicBoxBlockEntityRenderer::new);
    }
}
