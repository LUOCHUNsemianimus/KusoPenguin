package com.luochun.kusopenguin.client.vocals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

/** Measures the same PCM handed to the sound engine in 20 ms windows. */
final class VocalPcmEnvelope implements AudioStream {
    private static final int WINDOW_MS = 20;
    private static final int MAX_WINDOWS = 300_000 / WINDOW_MS + 1;
    private static final float SILENCE_DBFS = -100.0F;
    private static final float QUIET_DBFS = -58.0F;
    private static final int REFERENCE_RADIUS = 750 / WINDOW_MS;
    private static final float MIN_RANGE_DB = 6.0F;
    private static final long MIN_HOLD_MS = 70;

    private final AudioStream source;
    private final float[] dbfs = new float[MAX_WINDOWS];
    private final int sampleRate;
    private final int windowFrames;
    private int frames;
    private double sumSquares;
    // One audio-thread writer. A volatile count publishes the completed, immutable windows.
    private volatile int published;

    // Playback/render thread only. The reference uses PCM the sound engine has already read.
    private final float[] reference = new float[REFERENCE_RADIUS * 2 + 1];
    private int referenceWindow = -1;
    private float referenceCenter;
    private float referenceRange;
    private int mouthLevel;
    private float smoothedDbfs;
    private long previousPosition = -1;
    private long levelSince;

    VocalPcmEnvelope(AudioStream source) {
        this.source = source;
        sampleRate = Math.round(source.getFormat().getSampleRate());
        windowFrames = (sampleRate * WINDOW_MS + 999) / 1000;
    }

    /** Returns rest, soft, open or wide at the current stream-relative playback position. */
    int mouthLevelAt(long positionMs) {
        long index = positionMs * sampleRate / (1000L * windowFrames);
        int count = published;
        if (positionMs < 0 || index >= count || dbfs[(int) index] <= QUIET_DBFS) {
            mouthLevel = 0;
            previousPosition = -1;
            return 0;
        }
        if (positionMs == previousPosition) {
            return mouthLevel;
        }
        int window = (int) index;
        float db = dbfs[window];
        if (previousPosition < 0 || positionMs < previousPosition) {
            mouthLevel = 0;
            smoothedDbfs = db;
            levelSince = positionMs - MIN_HOLD_MS;
        } else {
            long elapsed = Math.min(positionMs - previousPosition, 250);
            double timeConstant = db > smoothedDbfs ? 25.0 : 65.0;
            smoothedDbfs += (db - smoothedDbfs) * (float) -Math.expm1(-elapsed / timeConstant);
        }
        previousPosition = positionMs;
        if (window != referenceWindow) {
            updateReference(window, count);
        }
        float strength = 0.5F + (smoothedDbfs - referenceCenter) / referenceRange;
        float wideThreshold = mouthLevel == 3 ? 0.62F : 0.70F;
        float openThreshold = mouthLevel >= 2 ? 0.26F : 0.34F;
        int desired = strength >= wideThreshold ? 3 : strength >= openThreshold ? 2 : 1;
        if (desired != mouthLevel && positionMs - levelSince >= MIN_HOLD_MS) {
            mouthLevel = desired;
            levelSince = positionMs;
        }
        return mouthLevel;
    }

    private void updateReference(int window, int count) {
        int length = 0;
        for (int i = Math.max(0, window - REFERENCE_RADIUS);
                i < Math.min(count, window + REFERENCE_RADIUS + 1); i++) {
            if (dbfs[i] > QUIET_DBFS) {
                reference[length++] = dbfs[i];
            }
        }
        Arrays.sort(reference, 0, length);
        // The current window is active, so at least one sample is present. Percentiles
        // keep silence and isolated peaks from setting the scale for the entire phrase.
        float low = reference[(length - 1) / 4];
        float high = reference[(length - 1) * 85 / 100];
        referenceCenter = (low + high) * 0.5F;
        referenceRange = Math.max(MIN_RANGE_DB, high - low);
        referenceWindow = window;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer buffer = source.read(size);
        var samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        if (!samples.hasRemaining() && size >= Short.BYTES && frames != 0) {
            publishWindow();
        }
        while (samples.hasRemaining() && published < dbfs.length) {
            int sample = samples.get();
            sumSquares += (double) sample * sample;
            if (++frames == windowFrames) {
                publishWindow();
            }
        }
        return buffer;
    }

    private void publishWindow() {
        int index = published;
        dbfs[index] = sumSquares == 0 ? SILENCE_DBFS
                : Math.max(SILENCE_DBFS, (float) (10.0 * Math.log10(
                        sumSquares / (frames * 32768.0 * 32768.0))));
        frames = 0;
        sumSquares = 0;
        published = index + 1;
    }

    @Override
    public AudioFormat getFormat() {
        return source.getFormat();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
