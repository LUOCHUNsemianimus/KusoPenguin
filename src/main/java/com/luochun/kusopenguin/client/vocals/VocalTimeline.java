package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.compat.MelodiesVocalCompat;
import com.luochun.kusopenguin.mixin.MelodyProgressAccessor;
import com.luochun.kusopenguin.vocals.VocalData;
import com.luochun.kusopenguin.vocals.VocalItems;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional client snapshot of Immersive Melodies' effective ensemble timeline.
 *
 * <p>A held microphone is handled before IM's melody progress: its persisted start component and
 * world game time form an independent clear-vocal clock, and a paused microphone never falls back
 * to another held instrument's voice. Ordinary instruments keep IM's effective melody, phase,
 * length, native loop counter and start; this facade never calls {@code tick}/{@code sync} for
 * them. All IM types are confined to the lazy nested implementation.</p>
 */
public final class VocalTimeline {
    private static final String MOD_ID = "immersive_melodies";
    private static final ResourceLocation MAID_ID =
        ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);
    private static final Map<Object, String> FINGERPRINTS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private VocalTimeline() {
    }

    /**
     * Immutable native-timeline snapshot. {@code startTime} is IM's effective start (leader
     * override included), {@code loop} is its native loop counter and {@code phaseMs} is its
     * current position inside the melody.
     */
    public record Snapshot(ItemStack stack, String melodyId, String fingerprint, long phaseMs,
                           long lengthMs, long loop, long startTime) {
    }

    /** Null unless the entity is an actual player or exact maid with a playing held instrument. */
    public static Snapshot snapshot(LivingEntity entity) {
        return AVAILABLE && supported(entity) ? Impl.snapshot(entity) : null;
    }

    static void clearCache() {
        FINGERPRINTS.clear();
    }

    private static boolean supported(LivingEntity entity) {
        return entity != null && (entity instanceof Player
            || MAID_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
    }

    private static final class Impl {
        private Impl() {
        }

        static Snapshot snapshot(LivingEntity entity) {
            ItemStack microphone = microphone(entity);
            if (microphone != null) {
                return microphoneSnapshot(entity, microphone);
            }

            ItemStack instrumentStack = null;
            for (ItemStack handItem : entity.getHandSlots()) {
                if (handItem.getItem() instanceof immersive_melodies.item.InstrumentItem instrument
                    && instrument.isPlaying(handItem)) {
                    instrumentStack = handItem;
                    break;
                }
            }
            if (instrumentStack == null) {
                return null;
            }

            immersive_melodies.client.MelodyProgress progress =
                immersive_melodies.client.MelodyProgressManager.INSTANCE.getProgress(entity);
            if (!(progress instanceof MelodyProgressAccessor accessor)) {
                return null;
            }
            ResourceLocation melodyId = progress.getCurrentlyPlaying();
            if (melodyId == null || melodyId.getPath().isEmpty()
                || melodyId.getPath().equals("default")) {
                return null;
            }
            immersive_melodies.resources.Melody melody = progress.getMelody();
            if (melody == null || melody == immersive_melodies.resources.Melody.DEFAULT) {
                return null;
            }
            long lengthMs = melody.getLength();
            if (lengthMs <= 0L) {
                return null;
            }
            String fingerprint = fingerprint(melody);
            if (fingerprint.isEmpty()) {
                return null;
            }
            return new Snapshot(instrumentStack, melodyId.toString(), fingerprint,
                progress.getTime(), lengthMs, accessor.kuso$loop(), accessor.kuso$getStartTime());
        }

        /** Main hand first, then off hand; a held microphone always owns the entity's voice. */
        private static ItemStack microphone(LivingEntity entity) {
            if (VocalItems.isMicrophone(entity.getMainHandItem())) {
                return entity.getMainHandItem();
            }
            return VocalItems.isMicrophone(entity.getOffhandItem())
                ? entity.getOffhandItem() : null;
        }

        private static Snapshot microphoneSnapshot(LivingEntity entity, ItemStack stack) {
            if (!VocalItems.playing(stack)) {
                return null;
            }
            VocalData.Binding binding = VocalData.microphoneBinding(stack);
            int durationMs = VocalData.microphoneDurationMs(stack);
            if (binding == null || durationMs <= 0) {
                return null;
            }
            long startTime = VocalItems.startTime(stack);
            long gameTime = entity.level().getGameTime();
            // Invalid imported clocks must stay silent, rather than reseeking the first phrase
            // every second until a future timestamp arrives. An explicit Play repairs the clock.
            if (startTime < 0L || startTime > gameTime) {
                return null;
            }
            long elapsedMs = (gameTime - startTime) * 50L;
            return new Snapshot(stack, VocalData.MICROPHONE_MELODY, binding.trackId(),
                elapsedMs % durationMs, durationMs, elapsedMs / durationMs, startTime);
        }

        private static String fingerprint(Object melody) {
            String cached = FINGERPRINTS.get(melody);
            if (cached != null) {
                return cached;
            }
            String fingerprint = MelodiesVocalCompat.fingerprint(melody);
            if (!fingerprint.isEmpty()) {
                FINGERPRINTS.put(melody, fingerprint);
            }
            return fingerprint;
        }
    }
}
