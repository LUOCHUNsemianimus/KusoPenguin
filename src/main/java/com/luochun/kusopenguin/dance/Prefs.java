package com.luochun.kusopenguin.dance;

import java.util.List;
import java.util.Objects;

/**
 * Immutable per-player dance preferences. Every selection is either a builtin id or one of
 * that player's own uploaded clip hashes. Empty slot strings mean "not assigned".
 */
public record Prefs(String music, String revival, String maidMusic, List<String> slots) {
    public static final int SLOT_COUNT = 8;
    public static final String EMPTY = "";

    public Prefs {
        music = requireId(music, "music");
        revival = requireId(revival, "revival");
        maidMusic = requireId(maidMusic, "maidMusic");
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

    /** Music cycle, revival pole, maid-music cycle, then hip/pole/wave/cycle and four empty slots. */
    public static Prefs defaults() {
        return new Prefs(DanceLibrary.BUILTIN_CYCLE, DanceLibrary.BUILTIN_POLE,
            DanceLibrary.BUILTIN_CYCLE,
            List.of(DanceLibrary.BUILTIN_HIP, DanceLibrary.BUILTIN_POLE,
                DanceLibrary.BUILTIN_WAVE, DanceLibrary.BUILTIN_CYCLE,
                EMPTY, EMPTY, EMPTY, EMPTY));
    }

    public Prefs with(Trigger trigger, String clipId) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case MUSIC -> new Prefs(clipId, revival, maidMusic, slots);
            case REVIVAL -> new Prefs(music, clipId, maidMusic, slots);
            case MAID_MUSIC -> new Prefs(music, revival, clipId, slots);
        };
    }

    public Prefs withSlot(int slot, String clipId) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Slot index out of range: " + slot);
        }
        String[] copy = slots.toArray(String[]::new);
        copy[slot] = clipId;
        return new Prefs(music, revival, maidMusic, List.of(copy));
    }

    private static String requireId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > DanceLibrary.MAX_CLIP_ID_CHARS) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }
}
