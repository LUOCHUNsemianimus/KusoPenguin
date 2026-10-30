package com.luochun.kusopenguin.compat;

import com.luochun.kusopenguin.music.WaveFile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional Immersive Melodies bridge for vocal bindings.
 *
 * <p>The facade and every public signature are free of Immersive Melodies types. The nested
 * implementation is only initialized when {@link ModList} reports the mod, so standalone and
 * dedicated-server class loading never reaches IM classes. This class is common-safe: it reads only
 * item components and server-side melody resources.
 */
public final class MelodiesVocalCompat {
    private static final String MOD_ID = "immersive_melodies";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);
    /** Fingerprint payload bound; a melody that encodes larger is rejected with an empty hash. */
    private static final int MAX_FINGERPRINT_BYTES = 4 * 1024 * 1024;

    private MelodiesVocalCompat() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** True only for an actual Immersive Melodies instrument item. */
    public static boolean isInstrument(ItemStack stack) {
        return AVAILABLE && stack != null && !stack.isEmpty() && Impl.isInstrument(stack);
    }

    /** The instrument's persisted IM playing flag; never a proxy for audio lifetime. */
    public static boolean playing(ItemStack stack) {
        return AVAILABLE && stack != null && !stack.isEmpty() && Impl.playing(stack);
    }

    /**
     * The exact selected melody resource id as stored on the instrument, or an empty string when
     * the stack is not an instrument.
     */
    public static String selectedMelody(ItemStack stack) {
        return AVAILABLE && stack != null && !stack.isEmpty() ? Impl.selectedMelody(stack) : "";
    }

    /**
     * Server-side content fingerprint for a melody id. Returns empty when the id is malformed, the
     * bridge is unavailable, or the id is absent from both the datapack map and the saved custom
     * index. Membership is checked first so a missing id never triggers a disk read or deletion.
     */
    public static String serverFingerprint(String melodyId) {
        if (!AVAILABLE || melodyId == null || melodyId.length() > 256) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(melodyId);
        return id == null ? "" : Impl.serverFingerprint(id);
    }

    /**
     * Shared content hash of an IM {@code Melody}, or empty for any other object. The melody is
     * encoded exactly once through IM's stream codec, bounded to 4 MiB, then hashed with the same
     * SHA-256 helper used by the existing audio library. Both sides may call this; callers must not
     * do it every server tick.
     */
    public static String fingerprint(Object melody) {
        return AVAILABLE && melody != null ? Impl.fingerprint(melody) : "";
    }

    /** Lazy IM-only implementation; no public signature exposes an IM type. */
    private static final class Impl {
        private Impl() {
        }

        static boolean isInstrument(ItemStack stack) {
            return stack.getItem() instanceof immersive_melodies.item.InstrumentItem;
        }

        static boolean playing(ItemStack stack) {
            return stack.getItem() instanceof immersive_melodies.item.InstrumentItem instrument
                    && instrument.isPlaying(stack);
        }

        static String selectedMelody(ItemStack stack) {
            if (!(stack.getItem() instanceof immersive_melodies.item.InstrumentItem)) {
                return "";
            }
            return immersive_melodies.item.InstrumentItem.getMelody(stack).toString();
        }

        static String serverFingerprint(ResourceLocation id) {
            var datapack = immersive_melodies.resources.ServerMelodyManager.getDatapackMelodies();
            if (datapack.containsKey(id)) {
                return fingerprint(immersive_melodies.resources.ServerMelodyManager.getMelody(id));
            }
            if (immersive_melodies.resources.ServerMelodyManager.server == null) {
                return "";
            }
            if (immersive_melodies.resources.ServerMelodyManager.getIndex()
                    .getMelodies().containsKey(id)) {
                return fingerprint(immersive_melodies.resources.ServerMelodyManager.getMelody(id));
            }
            return "";
        }

        static String fingerprint(Object value) {
            if (!(value instanceof immersive_melodies.resources.Melody melody)) {
                return "";
            }
            // The capacity itself enforces the bound while the codec writes; a melody larger than
            // 4 MiB is rejected before it can finish allocating the oversized payload.
            ByteBuf buffer = Unpooled.buffer(256, MAX_FINGERPRINT_BYTES);
            try {
                FriendlyByteBuf friendly = new FriendlyByteBuf(buffer);
                immersive_melodies.resources.Melody.STREAM_CODEC.encode(friendly, melody);
                int length = buffer.readableBytes();
                if (length > MAX_FINGERPRINT_BYTES) {
                    return "";
                }
                byte[] bytes = new byte[length];
                buffer.getBytes(buffer.readerIndex(), bytes);
                return WaveFile.hash(bytes);
            } catch (IndexOutOfBoundsException e) {
                if (isCapacityRejection(e)) {
                    return "";
                }
                throw e;
            } finally {
                buffer.release();
            }
        }

        private static boolean isCapacityRejection(IndexOutOfBoundsException e) {
            String message = e.getMessage();
            return message != null && message.contains("maxCapacity");
        }
    }
}
