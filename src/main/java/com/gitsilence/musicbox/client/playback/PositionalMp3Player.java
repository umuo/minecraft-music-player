package com.gitsilence.musicbox.client.playback;

import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.world.phys.Vec3;

final class PositionalMp3Player implements AutoCloseable {
    private static final int MAX_PCM_BYTES = 96 * 1024 * 1024;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ChannelAccess.ChannelHandle handle;
    private volatile SoundBuffer soundBuffer;
    private final Decoded decoded;

    PositionalMp3Player(InputStream input) throws Exception {
        decoded = decode(input);
    }

    void play(Vec3 position, float attenuationDistance) throws Exception {
        if (closed.get()) return;
        soundBuffer = new SoundBuffer(decoded.pcm(), decoded.format());
        CompletableFuture<ChannelAccess.ChannelHandle> future = Minecraft.getInstance().getSoundManager()
                .soundEngine.channelAccess.createHandle(Library.Pool.STREAMING);
        handle = future.join();
        if (handle == null || closed.get()) { close(); return; }
        handle.execute(channel -> {
            channel.setSelfPosition(position);
            channel.linearAttenuation(attenuationDistance);
            channel.setLooping(false);
            channel.attachStaticBuffer(soundBuffer);
            channel.play();
        });
        while (!closed.get()) Thread.sleep(100L);
    }

    private static Decoded decode(InputStream input) throws Exception {
        Bitstream bitstream = new Bitstream(input);
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = 44_100;
        int channels = 2;
        Header header;
        while ((header = bitstream.readFrame()) != null) {
            SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            sampleRate = samples.getSampleFrequency();
            channels = samples.getChannelCount();
            for (int i = 0; i < samples.getBufferLength(); i++) {
                short value = samples.getBuffer()[i];
                pcm.write(value & 0xFF);
                pcm.write((value >>> 8) & 0xFF);
            }
            bitstream.closeFrame();
            if (pcm.size() > MAX_PCM_BYTES) throw new IllegalStateException("Decoded audio exceeds memory limit");
        }
        byte[] bytes = pcm.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN).put(bytes).flip();
        return new Decoded(buffer, new AudioFormat(sampleRate, 16, channels, true, false));
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ChannelAccess.ChannelHandle current = handle;
        if (current != null) {
            SoundBuffer buffer = soundBuffer;
            current.execute(channel -> {
                channel.stop();
                if (buffer != null) buffer.discardAlBuffer();
            });
            current.release();
        }
    }

    private record Decoded(ByteBuffer pcm, AudioFormat format) { }
}
