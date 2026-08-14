package com.gitsilence.musicbox.block.entity;

import com.gitsilence.musicbox.MusicBoxMod;
import com.gitsilence.musicbox.playback.TrackRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class MusicBoxBlockEntity extends BlockEntity {
    private static final String TRACK_TAG = "Track";
    private static final String START_TIME_TAG = "StartGameTime";
    private static final String QUEUE_TAG = "Queue";

    @Nullable
    private TrackRef currentTrack;
    private long startGameTime;
    private final List<TrackRef> queue = new ArrayList<>();
    private long requestGeneration;

    public MusicBoxBlockEntity(BlockPos pos, BlockState state) {
        super(MusicBoxMod.MUSIC_BOX_ENTITY.get(), pos, state);
    }

    public void start(TrackRef track, long startGameTime) {
        this.currentTrack = track;
        this.startGameTime = startGameTime;
        setChanged();
    }

    public void stop() {
        requestGeneration++;
        this.currentTrack = null;
        this.startGameTime = 0;
        setChanged();
    }

    public long beginResolution() { return ++requestGeneration; }

    public boolean isCurrentResolution(long generation) { return requestGeneration == generation; }

    public boolean enqueue(TrackRef track, int maximum) {
        if (!track.isValid() || queue.size() >= maximum) return false;
        queue.add(track);
        setChanged();
        return true;
    }

    @Nullable
    public TrackRef pollQueue() {
        if (queue.isEmpty()) return null;
        TrackRef track = queue.removeFirst();
        setChanged();
        return track;
    }

    public boolean removeQueued(int index) {
        if (index < 0 || index >= queue.size()) return false;
        queue.remove(index);
        setChanged();
        return true;
    }

    public void clearQueue() {
        queue.clear();
        setChanged();
    }

    public List<TrackRef> queue() {
        return List.copyOf(queue);
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
            tag.put(TRACK_TAG, saveTrack(currentTrack));
            tag.putLong(START_TIME_TAG, startGameTime);
        }
        net.minecraft.nbt.ListTag queueTag = new net.minecraft.nbt.ListTag();
        for (TrackRef track : queue) queueTag.add(saveTrack(track));
        tag.put(QUEUE_TAG, queueTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(TRACK_TAG)) {
            currentTrack = null;
            startGameTime = 0;
        } else {
            TrackRef loaded = loadTrack(tag.getCompound(TRACK_TAG));
            currentTrack = loaded.isValid() ? loaded : null;
            startGameTime = currentTrack == null ? 0 : tag.getLong(START_TIME_TAG);
        }
        queue.clear();
        net.minecraft.nbt.ListTag queueTag = tag.getList(QUEUE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < queueTag.size(); index++) {
            TrackRef queued = loadTrack(queueTag.getCompound(index));
            if (queued.isValid()) queue.add(queued);
        }
    }

    private static CompoundTag saveTrack(TrackRef track) {
        CompoundTag trackTag = new CompoundTag();
        trackTag.putString("Source", track.source());
        trackTag.putString("Id", track.trackId());
        trackTag.putString("Title", track.title());
        trackTag.putString("Artist", track.artist());
        trackTag.putString("Album", track.album());
        trackTag.putInt("Duration", track.durationSeconds());
        trackTag.putString("Hash128", track.hash128());
        trackTag.putString("Hash320", track.hash320());
        trackTag.putString("HashFlac", track.hashFlac());
        trackTag.putString("HashHires", track.hashHires());
        trackTag.putString("Quality", track.quality());
        trackTag.putString("ResolverSource", track.sourceId());
        return trackTag;
    }

    private static TrackRef loadTrack(CompoundTag trackTag) {
        return new TrackRef(
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
                trackTag.getString("Quality"),
                trackTag.contains("ResolverSource") ? trackTag.getString("ResolverSource") : "default"
        );
    }
}
