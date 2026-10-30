package com.luochun.kusopenguin.client.music;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.client.sounds.AudioStream;

/**
 * Streaming signed PCM16 little-endian mono WAV reader shared by discs and vocals.
 *
 * <p>{@link #open(Path, long)} applies all format validation and skips to the requested millisecond
 * position before returning. The caller owns the returned stream and must close it. A failed open
 * closes the underlying file descriptor before throwing.</p>
 */
public final class CachedPcmStream implements AudioStream {
    private final AudioInputStream stream;
    private final long remainingFrames;
    private final int sampleRate;
    private int pendingByte = -1;

    private CachedPcmStream(AudioInputStream stream, long remainingFrames, int sampleRate) {
        this.stream = stream;
        this.remainingFrames = remainingFrames;
        this.sampleRate = sampleRate;
    }

    /** Opens and positions a canonical WAV without ever buffering the complete track. */
    public static CachedPcmStream open(Path path, long offsetMs) throws IOException {
        Objects.requireNonNull(path, "path");
        AudioInputStream stream = null;
        try {
            stream = AudioSystem.getAudioInputStream(path.toFile());
            AudioFormat format = stream.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16 || format.getChannels() != 1
                || format.isBigEndian()) {
                throw new IOException("Cached audio is not signed 16-bit little-endian mono PCM");
            }
            int rate = Math.round(format.getSampleRate());
            if (rate < 8000 || rate > 48000) {
                throw new IOException("Cached audio sample rate is outside 8000-48000 Hz");
            }

            long totalFrames = stream.getFrameLength();
            if (totalFrames < 0L) {
                totalFrames = stream.available() / 2L;
            }
            long safeOffset = Math.max(0L, offsetMs);
            long maxOffsetMs = totalFrames * 1000L / rate;
            long skipFrames = safeOffset >= maxOffsetMs
                ? totalFrames : safeOffset * rate / 1000L;
            stream.skipNBytes(skipFrames * 2L);

            long remaining = Math.max(0L, totalFrames - skipFrames);
            CachedPcmStream result = new CachedPcmStream(stream, remaining, rate);
            stream = null;
            return result;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Cached audio is not a readable WAV file", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // The original open/validation failure is the useful error.
                }
            }
        }
    }

    /** Remaining playback duration from the requested offset, bounded to the file. */
    public long remainingDurationMs() {
        return remainingFrames * 1000L / sampleRate;
    }

    @Override
    public AudioFormat getFormat() {
        return stream.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        int capacity = size & ~1;
        if (capacity <= 0) {
            return ByteBuffer.allocateDirect(0);
        }
        int requested = capacity + (pendingByte >= 0 ? 1 : 0);
        byte[] bytes = new byte[requested];
        int count = readAligned(bytes, requested);
        if (count <= 0) {
            return ByteBuffer.allocateDirect(0);
        }
        int usable = count & ~1;
        ByteBuffer direct = ByteBuffer.allocateDirect(usable);
        direct.put(bytes, 0, usable);
        direct.flip();
        return direct;
    }

    private int readAligned(byte[] target, int length) throws IOException {
        int count;
        if (pendingByte >= 0 && length > 0) {
            target[0] = (byte) pendingByte;
            pendingByte = -1;
            int read = stream.read(target, 1, length - 1);
            count = read < 0 ? 1 : 1 + read;
        } else {
            count = stream.read(target, 0, length);
        }
        if (count > 0 && (count & 1) != 0) {
            pendingByte = target[count - 1] & 0xff;
            count--;
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
