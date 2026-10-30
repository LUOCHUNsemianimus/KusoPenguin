package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.music.DiscPackets;
import com.luochun.kusopenguin.music.MusicItems;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
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
        Level level = Minecraft.getInstance().level;
        long elapsedMs = level == null ? 0L
            : Math.max(0L, level.getGameTime() - source.startedGameTime()) * 50L;
        return CachedPcmStream.open(cacheFile, elapsedMs);
    }
}
