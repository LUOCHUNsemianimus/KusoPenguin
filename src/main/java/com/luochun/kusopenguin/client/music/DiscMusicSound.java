package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.music.DiscPackets;
import com.luochun.kusopenguin.music.MusicItems;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Streaming positional sound for a downloaded canonical WAV.
 *
 * <p>The stream is opened on Minecraft's non-critical IO pool and skips to the record's current
 * position using the current client game time, so a late download or late join aligns with the
 * server-started record instead of restarting it. The returned stream owns the file and exposes
 * bounded signed PCM16 little-endian reads as direct buffers.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class DiscMusicSound extends AbstractTickableSoundInstance {
    private final DiscPackets.Playback source;
    private final Path cacheFile;
    private final BooleanSupplier valid;

    public DiscMusicSound(DiscPackets.Playback source, Path cacheFile, BooleanSupplier valid) {
        super(MusicItems.CUSTOM_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.source = Objects.requireNonNull(source, "source");
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile");
        this.valid = Objects.requireNonNull(valid, "valid");
        this.volume = 4.0F;
        this.looping = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = source.pos().getX() + 0.5D;
        this.y = source.pos().getY() + 0.5D;
        this.z = source.pos().getZ() + 0.5D;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (!valid.getAsBoolean() || level == null
            || !level.dimension().location().equals(source.dimension())) {
            stop();
            return;
        }
        if (level.getGameTime() >= source.startedGameTime() + source.durationTicks()) {
            stop();
        }
    }

    /** SoundManager calls this for a non-tickable stop; expose it for the later integration. */
    public void end() {
        stop();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound,
                                                   boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return openCachedStream();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    private AudioStream openCachedStream() throws IOException {
        AudioInputStream stream = null;
        try {
            stream = AudioSystem.getAudioInputStream(cacheFile.toFile());
            AudioFormat format = stream.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16 || format.getChannels() != 1
                || format.isBigEndian()) {
                throw new IOException("Cached disc is not signed 16-bit little-endian mono PCM");
            }
            int rate = Math.round(format.getSampleRate());
            if (rate < 8000 || rate > 48000) {
                throw new IOException("Cached disc sample rate is outside 8000-48000 Hz");
            }

            long totalFrames = stream.getFrameLength();
            if (totalFrames < 0L) {
                totalFrames = stream.available() / 2L;
            }
            long elapsedTicks = 0L;
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                elapsedTicks = Math.max(0L, level.getGameTime() - source.startedGameTime());
            }
            long skipFrames = Math.min(totalFrames, elapsedTicks * rate / 20L);
            stream.skipNBytes(skipFrames * 2L);

            AudioInputStream result = stream;
            stream = null;
            return new CachedWavStream(result);
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Cached disc is not a readable WAV file", e);
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

    private static final class CachedWavStream implements AudioStream {
        private final AudioInputStream stream;
        private int pendingByte = -1;

        private CachedWavStream(AudioInputStream stream) {
            this.stream = stream;
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
}
