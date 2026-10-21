package com.luochun.kusopenguin.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Strict, server-side WAV inspection for the normalized music upload contract.
 *
 * <p>The only accepted format is canonical RIFF/WAVE PCM format 1: mono, signed 16-bit
 * little-endian, 8000-48000 Hz. The encoder below emits the canonical 44-byte header. The
 * inspector still walks every RIFF chunk (including unknown chunks) so uploaded files cannot
 * smuggle data through an inconsistent RIFF size or an unaccounted odd-sized chunk.</p>
 */
public final class WaveFile {
    /** Maximum accepted file (and therefore stored PCM) size. */
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    /** Maximum accepted duration. */
    public static final int MAX_SECONDS = 300;
    /** Maximum PCM bytes carried by one upload/download chunk packet. */
    public static final int MAX_CHUNK = 16384;

    private static final int CANONICAL_HEADER_BYTES = 44;
    private static final long MAX_DURATION_TICKS = MAX_SECONDS * 20L;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private WaveFile() {
    }

    /**
     * Parsed PCM bounds.
     *
     * @param sampleRate sample rate in Hz
     * @param dataOffset offset of the first PCM byte inside the inspected file array
     * @param pcmBytes   number of PCM bytes
     */
    public record Header(int sampleRate, int dataOffset, int pcmBytes) {
        /** Duration rounded up to whole game ticks (20 ticks per second). */
        public int durationTicks() {
            long frames = pcmBytes / 2L;
            return (int) ((frames * 20L + sampleRate - 1L) / sampleRate);
        }
    }

    /**
     * Inspect a complete WAV file and return its PCM bounds.
     *
     * @throws IOException if the bytes are not a strict, supported RIFF/WAVE PCM16 mono file
     */
    public static Header inspect(byte[] file) throws IOException {
        if (file == null) {
            throw new IOException("WAV data is null");
        }
        if (file.length < 12) {
            throw new IOException("WAV file is too short for a RIFF header: " + file.length + " bytes");
        }
        if (file.length > MAX_BYTES) {
            throw new IOException("WAV file exceeds the 32 MiB limit: " + file.length + " bytes");
        }
        if (!chunkIdEquals(file, 0, "RIFF")) {
            throw new IOException("WAV file does not start with RIFF");
        }
        if (!chunkIdEquals(file, 8, "WAVE")) {
            throw new IOException("RIFF file is not WAVE");
        }

        long riffSize = readU32(file, 4);
        long expectedRiffSize = file.length - 8L;
        if (riffSize != expectedRiffSize) {
            throw new IOException("WAV RIFF size " + riffSize + " does not match file size "
                + file.length + " (expected " + expectedRiffSize + ")");
        }

        int sampleRate = -1;
        int dataOffset = -1;
        int pcmBytes = -1;
        boolean sawFmt = false;
        boolean sawData = false;

        int offset = 12;
        while (offset < file.length) {
            if (file.length - offset < 8) {
                throw new IOException("Truncated RIFF chunk header at offset " + offset);
            }
            String id = chunkId(file, offset);
            long size = readU32(file, offset + 4);
            long dataStart = (long) offset + 8L;
            long dataEnd = dataStart + size;
            // Long arithmetic must stay inside the array before converting to int offsets.
            if (dataEnd < dataStart || dataEnd > file.length) {
                throw new IOException("RIFF chunk '" + id + "' at offset " + offset + " overruns the file");
            }

            if ("fmt ".equals(id)) {
                if (sawFmt) {
                    throw new IOException("WAV contains more than one fmt chunk");
                }
                if (size < 16) {
                    throw new IOException("WAV fmt chunk is too small: " + size + " bytes");
                }
                int format = readU16(file, dataStart);
                int channels = readU16(file, dataStart + 2);
                long rate = readU32(file, dataStart + 4);
                long byteRate = readU32(file, dataStart + 8);
                int blockAlign = readU16(file, dataStart + 12);
                int bits = readU16(file, dataStart + 14);

                if (format != 1) {
                    throw new IOException("Unsupported WAV format tag " + format + "; only PCM format 1 is accepted");
                }
                if (channels != 1) {
                    throw new IOException("Unsupported WAV channel count " + channels + "; only mono is accepted");
                }
                if (rate < 8000L || rate > 48000L) {
                    throw new IOException("Unsupported WAV sample rate " + rate + " Hz; expected 8000-48000 Hz");
                }
                if (bits != 16) {
                    throw new IOException("Unsupported WAV bit depth " + bits + "; only signed 16-bit is accepted");
                }
                if (blockAlign != 2) {
                    throw new IOException("Invalid WAV block alignment " + blockAlign + "; mono PCM16 requires 2");
                }
                if (byteRate != rate * 2L) {
                    throw new IOException("Invalid WAV byte rate " + byteRate + " for " + rate + " Hz mono PCM16");
                }
                if (size == 16) {
                    // Canonical layout with no extension.
                } else if (size == 18) {
                    int cbSize = readU16(file, dataStart + 16);
                    if (cbSize != 0) {
                        throw new IOException("WAV fmt extension declares " + cbSize + " extra bytes, but only 18 bytes are present");
                    }
                } else if (size > 18) {
                    int cbSize = readU16(file, dataStart + 16);
                    if (cbSize != size - 18L) {
                        throw new IOException("WAV fmt extension size mismatch: header says " + (size - 18L)
                            + " bytes, cbSize says " + cbSize);
                    }
                } else {
                    throw new IOException("WAV fmt chunk is too small: " + size + " bytes");
                }

                sampleRate = (int) rate;
                sawFmt = true;
            } else if ("data".equals(id)) {
                if (!sawFmt) {
                    throw new IOException("WAV data chunk appears before the fmt chunk");
                }
                if (sawData) {
                    throw new IOException("WAV contains more than one data chunk");
                }
                if (size == 0L) {
                    throw new IOException("WAV data chunk is empty");
                }
                if ((size & 1L) != 0L) {
                    throw new IOException("WAV PCM16 data must contain an even number of bytes: " + size);
                }
                if (size > MAX_BYTES) {
                    throw new IOException("WAV PCM data exceeds the 32 MiB limit: " + size + " bytes");
                }

                long frames = size / 2L;
                long durationTicks = (frames * 20L + sampleRate - 1L) / sampleRate;
                if (durationTicks > MAX_DURATION_TICKS) {
                    throw new IOException("WAV duration " + durationTicks + " ticks exceeds the 300 second limit");
                }

                dataOffset = (int) dataStart;
                pcmBytes = (int) size;
                sawData = true;
            }

            long next = dataEnd + (size & 1L);
            if (next > file.length) {
                throw new IOException("RIFF chunk '" + id + "' at offset " + offset + " is missing its padding byte");
            }
            if (next <= offset) {
                throw new IOException("RIFF chunk at offset " + offset + " does not advance");
            }
            offset = (int) next;
        }

        if (!sawFmt) {
            throw new IOException("WAV has no fmt chunk");
        }
        if (!sawData) {
            throw new IOException("WAV has no data chunk");
        }
        return new Header(sampleRate, dataOffset, pcmBytes);
    }

    /**
     * Encode mono signed 16-bit little-endian PCM as a canonical 44-byte WAV and validate it.
     *
     * @throws IOException if the PCM or sample rate is outside the common contract
     */
    public static byte[] encode(byte[] monoPcm, int rate) throws IOException {
        if (monoPcm == null) {
            throw new IOException("PCM data is null");
        }
        if (monoPcm.length == 0) {
            throw new IOException("PCM data is empty");
        }
        if ((monoPcm.length & 1) != 0) {
            throw new IOException("PCM16 data must contain an even number of bytes");
        }
        if (rate < 8000 || rate > 48000) {
            throw new IOException("Unsupported sample rate " + rate + " Hz; expected 8000-48000 Hz");
        }
        long total = (long) CANONICAL_HEADER_BYTES + monoPcm.length;
        if (total > MAX_BYTES) {
            throw new IOException("WAV file exceeds the 32 MiB limit: " + total + " bytes");
        }
        long frames = monoPcm.length / 2L;
        long durationTicks = (frames * 20L + rate - 1L) / rate;
        if (durationTicks > MAX_DURATION_TICKS) {
            throw new IOException("WAV duration " + durationTicks + " ticks exceeds the 300 second limit");
        }

        byte[] out = new byte[(int) total];
        writeChunkId(out, 0, "RIFF");
        writeU32(out, 4, total - 8L);
        writeChunkId(out, 8, "WAVE");
        writeChunkId(out, 12, "fmt ");
        writeU32(out, 16, 16L);
        writeU16(out, 20, 1);
        writeU16(out, 22, 1);
        writeU32(out, 24, rate);
        writeU32(out, 28, rate * 2L);
        writeU16(out, 32, 2);
        writeU16(out, 34, 16);
        writeChunkId(out, 36, "data");
        writeU32(out, 40, monoPcm.length);
        System.arraycopy(monoPcm, 0, out, CANONICAL_HEADER_BYTES, monoPcm.length);

        // The encoder must never produce bytes that the strict inspector would reject.
        inspect(out);
        return out;
    }

    /** SHA-256 of the complete WAV bytes as lowercase hexadecimal. */
    public static String hash(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                out[i * 2] = HEX[value >>> 4];
                out[i * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean chunkIdEquals(byte[] bytes, int offset, String id) {
        for (int i = 0; i < 4; i++) {
            if ((bytes[offset + i] & 0xff) != id.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String chunkId(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int readU16(byte[] bytes, long offset) {
        int o = (int) offset;
        return (bytes[o] & 0xff) | ((bytes[o + 1] & 0xff) << 8);
    }

    private static long readU32(byte[] bytes, long offset) {
        int o = (int) offset;
        return (bytes[o] & 0xffL)
            | ((bytes[o + 1] & 0xffL) << 8)
            | ((bytes[o + 2] & 0xffL) << 16)
            | ((bytes[o + 3] & 0xffL) << 24);
    }

    private static void writeChunkId(byte[] bytes, int offset, String id) {
        for (int i = 0; i < 4; i++) {
            bytes[offset + i] = (byte) id.charAt(i);
        }
    }

    private static void writeU16(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeU32(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
