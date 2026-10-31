package com.luochun.kusopenguin.compat;

import com.luochun.kusopenguin.vocals.VocalItems;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional, common-safe Immersive Melodies facade for the dance actor manager.
 *
 * <p>The outer facade never exposes an IM/TLM type. The nested implementation is initialized only
 * when {@link ModList} reports Immersive Melodies and an actual instrument query reaches it, so a
 * standalone or dedicated server without the mod keeps every previous behaviour. Maid ownership is
 * read from the exact {@code touhou_little_maid:maid} type through the same lazy bridge.</p>
 */
public final class MelodiesDanceCompat {
    private static final String MOD_ID = "immersive_melodies";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);
    private static final ResourceLocation MAID_ID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");

    private MelodiesDanceCompat() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** Exact entity type, never a subclass or a similar maid variant. */
    public static boolean isMaid(LivingEntity entity) {
        return entity != null && MAID_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** Owner UUID of an exact TLM maid, or {@code null} for anything else / without TLM. */
    public static UUID maidOwner(LivingEntity entity) {
        if (!ModList.get().isLoaded("touhou_little_maid") || !isMaid(entity)) {
            return null;
        }
        return MaidImpl.maidOwner(entity);
    }

    /**
     * True only while an actual Immersive Melodies instrument is in the PLAYING state. Holding a
     * paused/stopped instrument deliberately returns false; the microphone counts as a performance
     * here, because it is an actual playing instrument, but it is excluded by
     * {@link #playingUploadedMidi(LivingEntity)}.
     */
    public static boolean playingInstrument(LivingEntity entity) {
        return AVAILABLE && entity != null && Impl.playing(entity);
    }

    /**
     * True only for a real instrument performance of an uploaded/pinned MIDI resource. Membership
     * is checked against IM's server index only (not datapack melodies) and the microphone is
     * excluded, so a vocal performance can never trigger the maid dance circle.
     */
    public static boolean playingUploadedMidi(LivingEntity entity) {
        return AVAILABLE && entity != null && Impl.playingUploadedMidi(entity);
    }

    private static final class Impl {
        private Impl() {
        }

        static boolean playing(LivingEntity entity) {
            return playingStack(entity.getMainHandItem()) || playingStack(entity.getOffhandItem());
        }

        private static boolean playingStack(ItemStack stack) {
            return stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof immersive_melodies.item.InstrumentItem instrument
                    && instrument.isPlaying(stack);
        }

        static boolean playingUploadedMidi(LivingEntity entity) {
            return uploaded(entity.getMainHandItem()) || uploaded(entity.getOffhandItem());
        }

        private static boolean uploaded(ItemStack stack) {
            if (stack == null || stack.isEmpty() || VocalItems.isMicrophone(stack)
                    || !(stack.getItem() instanceof immersive_melodies.item.InstrumentItem instrument)
                    || !instrument.isPlaying(stack)) {
                return false;
            }
            ResourceLocation melody = immersive_melodies.item.InstrumentItem.getMelody(stack);
            if (melody == null || immersive_melodies.resources.ServerMelodyManager.server == null) {
                return false;
            }
            return immersive_melodies.resources.ServerMelodyManager.getIndex()
                    .getMelodies().containsKey(melody);
        }

    }

    /** Ownership works without Immersive Melodies; never resolve IM types on this path. */
    private static final class MaidImpl {
        static UUID maidOwner(LivingEntity entity) {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                    ? maid.getOwnerUUID() : null;
        }
    }
}
