package com.gitsilence.musicbox.block.entity;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class MusicBoxBlockEntity extends BlockEntity {
    private static final String TRACK_TAG = "Track";
    private static final String START_TIME_TAG = "StartGameTime";

    @Nullable
    private TrackRef currentTrack;
    private long startGameTime;

    public MusicBoxBlockEntity(BlockPos pos, BlockState state) {
        super(MusicBoxMod.MUSIC_BOX_ENTITY.get(), pos, state);
    }

    public void start(TrackRef track, long startGameTime) {
        this.currentTrack = track;
        this.startGameTime = startGameTime;
        setChanged();
    }

    public void stop() {
        this.currentTrack = null;
        this.startGameTime = 0;
        setChanged();
    }

    @Nullable
    public TrackRef currentTrack() {
        return currentTrack;
    }

    public long startGameTime() {
        return startGameTime;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (currentTrack != null) {
            CompoundTag trackTag = new CompoundTag();
            trackTag.putString("Source", currentTrack.source());
            trackTag.putString("Id", currentTrack.trackId());
            trackTag.putString("Title", currentTrack.title());
            trackTag.putString("Artist", currentTrack.artist());
            trackTag.putString("Album", currentTrack.album());
            trackTag.putInt("Duration", currentTrack.durationSeconds());
            trackTag.putString("Hash128", currentTrack.hash128());
            trackTag.putString("Hash320", currentTrack.hash320());
            trackTag.putString("HashFlac", currentTrack.hashFlac());
            trackTag.putString("HashHires", currentTrack.hashHires());
            trackTag.putString("Quality", currentTrack.quality());
            tag.put(TRACK_TAG, trackTag);
            tag.putLong(START_TIME_TAG, startGameTime);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(TRACK_TAG)) {
            currentTrack = null;
            startGameTime = 0;
            return;
        }
        CompoundTag trackTag = tag.getCompound(TRACK_TAG);
        TrackRef loaded = new TrackRef(
                trackTag.getString("Source"),
                trackTag.getString("Id"),
                trackTag.getString("Title"),
                trackTag.getString("Artist"),
                trackTag.getString("Album"),
                trackTag.getInt("Duration"),
                trackTag.getString("Hash128"),
                trackTag.getString("Hash320"),
                trackTag.getString("HashFlac"),
                trackTag.getString("HashHires"),
                trackTag.getString("Quality")
        );
        currentTrack = loaded.isValid() ? loaded : null;
        startGameTime = currentTrack == null ? 0 : tag.getLong(START_TIME_TAG);
    }
}
