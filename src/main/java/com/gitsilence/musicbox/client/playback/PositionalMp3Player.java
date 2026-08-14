package com.gitsilence.musicbox.client.playback;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.world.phys.Vec3;

final class PositionalMp3Player implements AutoCloseable {
    private static final int MAX_PCM_BYTES = 96 * 1024 * 1024;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Decoded decoded;
    private volatile SourceDataLine line;
    private volatile float currentVolume = 1.0f;

    PositionalMp3Player(InputStream input) throws Exception {
        decoded = decode(input);
    }

    void setVolume(float volume) {
        this.currentVolume = Math.max(0.0f, Math.min(1.0f, volume));
        SourceDataLine activeLine = line;
        if (activeLine != null && activeLine.isOpen() && activeLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) activeLine.getControl(FloatControl.Type.MASTER_GAIN);
            float min = gainControl.getMinimum();
            float max = gainControl.getMaximum();
            // Map 0.0-1.0 to logarithmic dB scale
            float db = (this.currentVolume <= 0.01f) ? min : (float) (Math.log10(this.currentVolume) * 20.0);
            gainControl.setValue(Math.max(min, Math.min(max, db)));
        }
    }

    void play(Vec3 position, float attenuationDistance) throws Exception {
        if (closed.get()) return;
        
        line = AudioSystem.getSourceDataLine(decoded.format());
        line.open(decoded.format());
        setVolume(currentVolume); // Apply initial volume
        line.start();

        com.gitsilence.musicbox.MusicBoxMod.LOGGER.info("Java Sound DataLine acquired, starting playback (duration={}ms)", decoded.durationMillis());

        byte[] pcm = decoded.pcmBytes();
        int offset = 0;
        int bufferSize = 4096;

        while (!closed.get() && offset < pcm.length) {
            int length = Math.min(bufferSize, pcm.length - offset);
            int written = line.write(pcm, offset, length);
            if (written > 0) {
                offset += written;
            } else {
                Thread.sleep(10);
            }
        }

        if (!closed.get()) {
            line.drain();
        }
        close();
    }

    private static Decoded decode(InputStream input) throws Exception {
        Bitstream bitstream = new Bitstream(input);
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = 44_100;
        int channels = 2;
        Header header;
        int frameCount = 0;
        
        while ((header = bitstream.readFrame()) != null) {
            frameCount++;
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
        if (bytes.length == 0) {
            throw new IllegalStateException("Decoded audio stream is empty or unsupported format (0 MP3 frames decoded)");
        }
        
        com.gitsilence.musicbox.MusicBoxMod.LOGGER.info("Decoded {} MP3 frames, {} bytes PCM, {} Hz, {} channels",
                frameCount, bytes.length, sampleRate, channels);
        long durationMillis = (long) ((bytes.length / (double) (sampleRate * channels * 2)) * 1000.0);
        return new Decoded(bytes, new AudioFormat(sampleRate, 16, channels, true, false), durationMillis);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        SourceDataLine activeLine = line;
        line = null;
        if (activeLine != null) {
            activeLine.stop();
            activeLine.flush();
            activeLine.close();
        }
    }

    private record Decoded(byte[] pcmBytes, AudioFormat format, long durationMillis) { }
}
