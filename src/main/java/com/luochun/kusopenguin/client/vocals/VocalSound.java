package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.client.music.CachedPcmStream;
import com.luochun.kusopenguin.music.MusicItems;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One positional, streaming vocal track for one living entity.
 *
 * <p>The stream is opened on Minecraft's non-critical IO pool at the latest expected audio
 * millisecond, so a late download/join starts at the current position instead of replaying the
 * intro. After opening, progress is estimated from that offset plus {@link System#nanoTime()}.
 * A MIDI cycle boundary or the actual stream end stops this instance; it never reopens a shortened
 * track on its own. The owner replaces it on the next native loop or a legitimate seek.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VocalSound extends AbstractTickableSoundInstance {
    private final LivingEntity entity;
    private final Path cacheFile;
    private final String trackId;
    private final int offsetMs;
    private final long midiEndMs;
    private final AtomicLong expectedAudioMs;
    private final ResourceKey<Level> dimension;
    private final boolean measureMouth;
    private volatile VocalPcmEnvelope envelope;
    private volatile long openedAtNanos = -1L;
    private volatile long openedAudioMs;
    private volatile long streamEndMs = Long.MAX_VALUE;
    private volatile boolean naturallyEnded;
    private volatile boolean streamFailed;
    private volatile String streamError = "";
    private volatile boolean paused;
    private volatile long pauseStartedNanos;
    private volatile boolean openedDuringPause;

    public VocalSound(LivingEntity entity, Path cacheFile, String trackId, int offsetMs,
                      long expectedAudioMs, long midiEndMs, float gain, boolean measureMouth) {
        super(MusicItems.CUSTOM_MUSIC.get(), SoundSource.RECORDS,
            SoundInstance.createUnseededRandom());
        this.entity = Objects.requireNonNull(entity, "entity");
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile");
        this.trackId = Objects.requireNonNull(trackId, "trackId");
        this.offsetMs = offsetMs;
        this.midiEndMs = midiEndMs;
        this.measureMouth = measureMouth;
        this.expectedAudioMs = new AtomicLong(Math.max(0L, expectedAudioMs));
        this.dimension = entity.level().dimension();
        this.volume = Math.max(0.0F, Math.min(2.0F, gain));
        this.looping = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = entity.getX();
        this.y = entity.getY() + entity.getBbHeight() * 0.5D;
        this.z = entity.getZ();
    }

    /** Latest server/IM-derived audio position; read atomically when the IO opener starts. */
    public void setExpectedAudioMs(long expectedAudioMs) {
        this.expectedAudioMs.set(Math.max(0L, expectedAudioMs));
    }

    /**
     * Freezes progress while the game is paused and shifts the opened timestamp by the excluded
     * duration. A stream that finishes opening during the pause starts its clock on unpause.
     */
    public void setPaused(boolean pause) {
        long now = System.nanoTime();
        if (pause) {
            if (!paused) {
                paused = true;
                pauseStartedNanos = now;
            }
            return;
        }
        if (!paused) {
            return;
        }
        long started = pauseStartedNanos;
        paused = false;
        pauseStartedNanos = 0L;
        if (openedAtNanos >= 0L) {
            if (openedDuringPause) {
                openedAtNanos = now;
            } else {
                openedAtNanos += Math.max(0L, now - started);
            }
        }
        openedDuringPause = false;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || paused) {
            return;
        }
        Level level = minecraft.level;
        if (entity.isRemoved() || entity.isDeadOrDying() || level == null
            || entity.level() != level || !level.dimension().equals(dimension)) {
            naturallyEnded = true;
            stop();
            return;
        }
        this.x = entity.getX();
        this.y = entity.getY() + entity.getBbHeight() * 0.5D;
        this.z = entity.getZ();
        if (openedAtNanos < 0L) {
            return;
        }
        long progress = audioProgressMs();
        if (midiEndMs >= 0L && progress >= midiEndMs) {
            naturallyEnded = true;
            stop();
            return;
        }
        if (progress >= streamEndMs) {
            naturallyEnded = true;
            stop();
        }
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound,
                                                    boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long requested = expectedAudioMs.get();
                CachedPcmStream stream = CachedPcmStream.open(cacheFile, requested);
                if (isStopped()) {
                    stream.close();
                    throw new IOException("Vocal sound stopped before its stream opened");
                }
                openedAudioMs = requested;
                streamEndMs = requested + stream.remainingDurationMs();
                openedDuringPause = paused || Minecraft.getInstance().isPaused();
                AudioStream output = stream;
                if (measureMouth) {
                    envelope = new VocalPcmEnvelope(stream);
                    output = envelope;
                }
                openedAtNanos = System.nanoTime();
                return output;
            } catch (IOException e) {
                streamFailed = true;
                streamError = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    /** Estimated position in the source audio file, or the latest expected position before open. */
    public long audioProgressMs() {
        long opened = openedAtNanos;
        if (opened < 0L) {
            return expectedAudioMs.get();
        }
        if (openedDuringPause) {
            return openedAudioMs;
        }
        long now = paused ? pauseStartedNanos : System.nanoTime();
        return openedAudioMs + Math.max(0L, now - opened) / 1_000_000L;
    }

    public boolean streamOpened() {
        return openedAtNanos >= 0L;
    }

    /** Relative amplitude at the actual stream position. Client thread only. */
    int mouthLevel() {
        VocalPcmEnvelope current = envelope;
        return current == null ? 0 : current.mouthLevelAt(audioProgressMs() - openedAudioMs);
    }

    /** True when this instance ended at EOF or a native MIDI boundary, not because it was replaced. */
    public boolean naturallyEnded() {
        return naturallyEnded;
    }

    public boolean streamFailed() {
        return streamFailed;
    }

    public String streamError() {
        return streamError;
    }

    public String trackId() {
        return trackId;
    }

    public long midiEndMs() {
        return midiEndMs;
    }

    public long streamEndMs() {
        return streamEndMs;
    }

    public int offsetMs() {
        return offsetMs;
    }

    /** Explicit stop hook for owner-driven replacement/range exit. */
    public void end() {
        stop();
    }
}
