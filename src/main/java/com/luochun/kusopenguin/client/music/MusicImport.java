package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.music.WaveFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side normalization of user-selected audio into the server's canonical mono PCM16 WAV.
 *
 * <p>All media are decoded incrementally. The file itself is limited to 32 MiB before decoding,
 * and the produced mono stream is checked against both the 32 MiB WAV payload bound and the
 * 300-second duration bound before each block is appended. The caller must run this on a worker
 * thread; this class owns no executor and never reads a whole input file into memory.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MusicImport {
    private static final int READ_BLOCK = 16384;
    private static final int MAX_MONO_BYTES = WaveFile.MAX_BYTES - 44;

    private MusicImport() {
    }

    /** Decode any supported input and return a canonical 44-byte-header WAV. */
    public static byte[] normalize(Path file) throws IOException {
        if (file == null) {
            throw new IOException("Audio file path is null");
        }
        long sourceSize = Files.size(file);
        if (sourceSize <= 0L) {
            throw new IOException("Audio file is empty");
        }
        if (sourceSize > WaveFile.MAX_BYTES) {
            throw new IOException("Audio file exceeds the 32 MiB limit: " + sourceSize + " bytes");
        }

        Decoded decoded = switch (detectFormat(file)) {
            case WAV -> decodeWav(file);
            case OGG -> decodeOgg(file);
            case MP3 -> decodeMp3(file);
        };
        if (decoded.mono().length == 0) {
            throw new IOException("Audio file contains no decodable samples");
        }
        return WaveFile.encode(decoded.mono(), decoded.sampleRate());
    }

    private static Format detectFormat(Path file) throws IOException {
        byte[] head = new byte[12];
        int read;
        try (InputStream input = Files.newInputStream(file)) {
            read = input.readNBytes(head, 0, head.length);
        }
        if (read >= 12 && matches(head, 0, "RIFF") && matches(head, 8, "WAVE")) {
            return Format.WAV;
        }
        if (read >= 4 && matches(head, 0, "OggS")) {
            return Format.OGG;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".wav")) {
            return Format.WAV;
        }
        if (name.endsWith(".ogg")) {
            return Format.OGG;
        }
        // ID3-prefixed and bare MPEG frame streams have no simple fixed header signature.
        return Format.MP3;
    }

    private static Decoded decodeWav(Path file) throws IOException {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat source = original.getFormat();
            int rate = Math.round(source.getSampleRate());
            int channels = source.getChannels();
            validateFormat(rate, channels, "WAV");

            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(), 16, channels, channels * 2, source.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, original)) {
                MonoWriter writer = new MonoWriter(rate, channels);
                int frameSize = channels * 2;
                byte[] block = new byte[READ_BLOCK];
                int carry = 0;
                while (true) {
                    checkInterrupted();
                    int count = pcm.read(block, carry, block.length - carry);
                    if (count < 0) {
                        break;
                    }
                    int total = carry + count;
                    int usable = total - total % frameSize;
                    writer.write(block, 0, usable);
                    carry = total - usable;
                    if (carry > 0) {
                        System.arraycopy(block, usable, block, 0, carry);
                    }
                }
                if (carry != 0) {
                    throw new IOException("Truncated WAV: incomplete sample frame");
                }
                writer.finish();
                return new Decoded(rate, writer.toByteArray());
            }
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported or invalid WAV file: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unsupported WAV format conversion: " + e.getMessage(), e);
        }
    }

    private static Decoded decodeOgg(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             JOrbisAudioStream ogg = new JOrbisAudioStream(input)) {
            AudioFormat format = ogg.getFormat();
            int rate = Math.round(format.getSampleRate());
            int channels = format.getChannels();
            validateFormat(rate, channels, "OGG");
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16 || format.isBigEndian()) {
                throw new IOException("OGG decoder did not produce signed 16-bit little-endian PCM");
            }

            MonoWriter writer = new MonoWriter(rate, channels);
            while (true) {
                checkInterrupted();
                ByteBuffer chunk;
                try {
                    chunk = ogg.read(READ_BLOCK);
                } catch (RuntimeException e) {
                    throw new IOException("Invalid OGG stream: " + e.getMessage(), e);
                }
                // read(16384) may return a slightly larger buffer; consume every actual byte.
                int actual = chunk.remaining();
                if (actual <= 0) {
                    break;
                }
                byte[] bytes = new byte[actual];
                chunk.get(bytes);
                writer.write(bytes, 0, actual);
            }
            writer.finish();
            return new Decoded(rate, writer.toByteArray());
        } catch (RuntimeException e) {
            throw new IOException("Invalid or truncated OGG file: " + e.getMessage(), e);
        }
    }

    private static Decoded decodeMp3(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            MonoWriter writer = null;
            SampleBuffer output = null;
            int rate = -1;
            int channels = -1;

            try {
                Header header;
                while ((header = bitstream.readFrame()) != null) {
                    checkInterrupted();
                    int frameRate = header.frequency();
                    int frameChannels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                    if (rate < 0) {
                        validateFormat(frameRate, frameChannels, "MP3");
                        rate = frameRate;
                        channels = frameChannels;
                        output = new SampleBuffer(rate, channels);
                        decoder.setOutputBuffer(output);
                        writer = new MonoWriter(rate, channels);
                    } else if (frameRate != rate || frameChannels != channels) {
                        throw new IOException("MP3 stream changes sample rate or channel count mid-stream");
                    }

                    SampleBuffer decoded;
                    try {
                        decoded = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    } catch (ClassCastException e) {
                        throw new IOException("MP3 decoder did not return a PCM sample buffer", e);
                    }
                    if (decoded.getChannelCount() != channels) {
                        throw new IOException("MP3 decoder channel count changed mid-stream");
                    }
                    int sampleCount = decoded.getBufferLength();
                    if (sampleCount % channels != 0) {
                        throw new IOException("Truncated MP3: incomplete sample frame");
                    }
                    writer.writeInterleaved(decoded.getBuffer(), sampleCount, channels);
                    bitstream.closeFrame();
                }
                if (writer == null) {
                    throw new IOException("MP3 contains no decodable frames");
                }
                writer.finish();
                return new Decoded(rate, writer.toByteArray());
            } finally {
                // Closing the outer stream also closes the BufferedInputStream held by Bitstream.
            }
        } catch (JavaLayerException e) {
            throw new IOException("Invalid or truncated MP3: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IOException("MP3 decoding failed: " + e.getMessage(), e);
        }
    }

    private static void validateFormat(int rate, int channels, String kind) throws IOException {
        if (rate < 8000 || rate > 48000) {
            throw new IOException(kind + " sample rate " + rate + " Hz is outside 8000-48000 Hz");
        }
        if (channels < 1 || channels > 8) {
            throw new IOException(kind + " channel count " + channels + " is outside 1-8");
        }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Audio normalization interrupted");
        }
    }

    private static boolean matches(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((data[offset + i] & 0xff) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private enum Format {
        WAV, OGG, MP3
    }

    private record Decoded(int sampleRate, byte[] mono) {
    }

    /**
     * Accumulates interleaved signed PCM16 frames and writes the average of all channels as mono
     * little-endian bytes. Bounds are checked before appending each block/frame, so a malformed
     * file cannot grow the output past the WAV contract.
     */
    private static final class MonoWriter {
        private final int sampleRate;
        private final int channels;
        private final int frameSize;
        private final long maxFrames;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024);
        private final byte[] pending;
        private int pendingBytes;
        private long frames;

        private MonoWriter(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.frameSize = channels * 2;
            this.maxFrames = (long) WaveFile.MAX_SECONDS * sampleRate;
            this.pending = new byte[frameSize];
        }

        private void write(byte[] source, int offset, int length) throws IOException {
            checkInterrupted();
            int completeFrames = (pendingBytes + length) / frameSize;
            checkBlock(completeFrames);

            while (length > 0) {
                int needed = frameSize - pendingBytes;
                int take = Math.min(needed, length);
                System.arraycopy(source, offset, pending, pendingBytes, take);
                pendingBytes += take;
                offset += take;
                length -= take;
                if (pendingBytes == frameSize) {
                    processFrame(pending, 0);
                    pendingBytes = 0;
                }
            }
        }

        private void writeInterleaved(short[] samples, int sampleCount, int channels) throws IOException {
            if (sampleCount % channels != 0) {
                throw new IOException("Truncated audio: incomplete interleaved frame");
            }
            int frameCount = sampleCount / channels;
            checkBlock(frameCount);
            for (int frame = 0; frame < frameCount; frame++) {
                int sum = 0;
                int base = frame * channels;
                for (int channel = 0; channel < channels; channel++) {
                    sum += samples[base + channel];
                }
                writeMono(sum / channels);
            }
        }

        private void processFrame(byte[] frame, int offset) throws IOException {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int lo = frame[offset + channel * 2] & 0xff;
                int hi = frame[offset + channel * 2 + 1];
                sum += (short) ((hi << 8) | lo);
            }
            // Signed integer averaging; channel count is at most 8, so the sum cannot overflow.
            writeMono(sum / channels);
        }

        private void writeMono(int sample) {
            output.write(sample & 0xff);
            output.write((sample >>> 8) & 0xff);
            frames++;
        }

        private void checkBlock(int newFrames) throws IOException {
            long nextFrames = frames + newFrames;
            if (nextFrames > maxFrames) {
                throw new IOException("Audio exceeds the 300 second limit");
            }
            if (nextFrames * 2L > MAX_MONO_BYTES) {
                throw new IOException("Decoded audio exceeds the 32 MiB limit");
            }
        }

        private void finish() throws IOException {
            if (pendingBytes != 0) {
                throw new IOException("Truncated audio: incomplete sample frame");
            }
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
