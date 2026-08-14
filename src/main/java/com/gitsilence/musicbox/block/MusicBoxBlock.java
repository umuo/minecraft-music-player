package com.gitsilence.musicbox.block;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.block.entity.MusicBoxBlockEntity;
import com.gitsilence.musicbox.network.payload.OpenMusicBoxPayload;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import com.gitsilence.musicbox.network.ServerPayloadHandlers;
import com.gitsilence.musicbox.network.payload.QueueStatePayload;
import net.minecraft.server.level.ServerLevel;
import com.gitsilence.musicbox.server.resolver.ResolverSourceRegistry;

public final class MusicBoxBlock extends BaseEntityBlock {
    public static final MapCodec<MusicBoxBlock> CODEC = simpleCodec(MusicBoxBlock::new);

    public MusicBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var publicSources = ResolverSourceRegistry.configuredSources().values().stream()
                    .filter(source -> source.enabled() && serverPlayer.hasPermissions(source.permissionLevel()))
                    .map(source -> source.publicInfo()).toList();
            PacketDistributor.sendToPlayer(serverPlayer, new OpenMusicBoxPayload(pos, publicSources));
            if (level.getBlockEntity(pos) instanceof MusicBoxBlockEntity musicBox) {
                PacketDistributor.sendToPlayer(serverPlayer, new QueueStatePayload(pos, musicBox.queue()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MusicBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != MusicBoxMod.MUSIC_BOX_ENTITY.get()) return null;
        return (tickerLevel, pos, tickerState, entity) -> {
            MusicBoxBlockEntity musicBox = (MusicBoxBlockEntity) entity;
            if (musicBox.currentTrack() != null && musicBox.currentTrack().durationSeconds() > 0
                    && tickerLevel.getGameTime() >= musicBox.startGameTime()
                    + musicBox.currentTrack().durationSeconds() * 20L) {
                ServerPayloadHandlers.advance((ServerLevel) tickerLevel, pos, musicBox);
            }
        };
    }
}
