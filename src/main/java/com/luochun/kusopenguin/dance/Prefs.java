package com.luochun.kusopenguin.dance;

import java.util.List;
import java.util.Objects;

/**
 * Immutable per-player dance preferences. Every selection is either a builtin id or one of
 * that player's own uploaded clip hashes. Empty slot strings mean "not assigned".
 */
public record Prefs(String music, String revival, String maidMusic, String animalMusic,
                    List<String> slots) {
    public static final int SLOT_COUNT = 8;
    public static final String EMPTY = "";

    public Prefs {
        music = requireId(music, "music");
        revival = requireId(revival, "revival");
        maidMusic = requireId(maidMusic, "maidMusic");
        animalMusic = requireId(animalMusic, "animalMusic");
        Objects.requireNonNull(slots, "slots");
        if (slots.size() != SLOT_COUNT) {
            throw new IllegalArgumentException("Prefs requires exactly " + SLOT_COUNT + " slots");
        }
        for (String slot : slots) {
            Objects.requireNonNull(slot, "slot");
            if (slot.length() > DanceLibrary.MAX_CLIP_ID_CHARS) {
                throw new IllegalArgumentException("Slot value is too long");
            }
        }
        slots = List.copyOf(slots);
    }

    /**
     * Compatibility overload for the pre-animal-channel shape. The animal jukebox channel keeps
     * using the same stored music choice, matching how old jukebox animals were choreographed.
     */
    public Prefs(String music, String revival, String maidMusic, List<String> slots) {
        this(music, revival, maidMusic, music, slots);
    }

    /**
     * Music cycle, revival pole, maid-music cycle, animal cycle, then hip/pole/wave/cycle and four
     * empty slots.
     */
    public static Prefs defaults() {
        return new Prefs(DanceLibrary.BUILTIN_CYCLE, DanceLibrary.BUILTIN_POLE,
            DanceLibrary.BUILTIN_CYCLE, DanceLibrary.BUILTIN_CYCLE,
            List.of(DanceLibrary.BUILTIN_HIP, DanceLibrary.BUILTIN_POLE,
                DanceLibrary.BUILTIN_WAVE, DanceLibrary.BUILTIN_CYCLE,
                EMPTY, EMPTY, EMPTY, EMPTY));
    }

    public Prefs with(Trigger trigger, String clipId) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case MUSIC -> new Prefs(clipId, revival, maidMusic, animalMusic, slots);
            case REVIVAL -> new Prefs(music, clipId, maidMusic, animalMusic, slots);
            case MAID_MUSIC -> new Prefs(music, revival, clipId, animalMusic, slots);
            case ANIMAL_MUSIC -> new Prefs(music, revival, maidMusic, clipId, slots);
        };
    }

    public Prefs withSlot(int slot, String clipId) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Slot index out of range: " + slot);
        }
        String[] copy = slots.toArray(String[]::new);
        copy[slot] = clipId;
        return new Prefs(music, revival, maidMusic, animalMusic, List.of(copy));
    }

    private static String requireId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > DanceLibrary.MAX_CLIP_ID_CHARS) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }
}
