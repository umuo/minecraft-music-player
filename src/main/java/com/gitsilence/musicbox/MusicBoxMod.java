package com.gitsilence.musicbox;

import com.gitsilence.musicbox.block.MusicBoxBlock;
import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.config.MusicBoxConfig;
import com.gitsilence.musicbox.network.MusicBoxNetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MusicBoxMod.MOD_ID)
public final class MusicBoxMod {
    public static final String MOD_ID = "musicbox";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<MusicBoxBlock> MUSIC_BOX = BLOCKS.register(
            "music_box",
            () -> new MusicBoxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUKEBOX))
    );
    public static final DeferredItem<BlockItem> MUSIC_BOX_ITEM = ITEMS.registerSimpleBlockItem("music_box", MUSIC_BOX);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MusicBoxBlockEntity>> MUSIC_BOX_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "music_box",
                    () -> BlockEntityType.Builder.of(MusicBoxBlockEntity::new, MUSIC_BOX.get()).build(null)
            );

    public MusicBoxMod(IEventBus modEventBus, ModContainer container) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(MusicBoxNetworking::register);

        container.registerConfig(ModConfig.Type.SERVER, MusicBoxConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MusicBoxConfig.CLIENT_SPEC);

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            modEventBus.addListener(com.gitsilence.musicbox.client.MusicBoxClientEvents::registerRenderers);
        }
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MUSIC_BOX_ITEM);
        }
    }
}
