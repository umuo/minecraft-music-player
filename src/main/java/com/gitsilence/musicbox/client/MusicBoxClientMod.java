package com.gitsilence.musicbox.client;

import com.gitsilence.musicbox.MusicBoxMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MusicBoxMod.MOD_ID, dist = Dist.CLIENT)
public final class MusicBoxClientMod {
    public MusicBoxClientMod(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
