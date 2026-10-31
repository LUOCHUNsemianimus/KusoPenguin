package com.luochun.kusopenguin.dance;

/**
 * Playback priority. Lower {@link #priority()} wins when two sources target the same entity.
 * A later runtime manager must never let a lower-priority source replace a higher one.
 */
public enum Source {
    /** Explicitly started by the owning player, below an active revival ritual. */
    MANUAL(0),
    /** Maid music trigger, gated by the exact owned-maid relationship later. */
    MAID_MUSIC(1),
    /** Jukebox / music-disc trigger. */
    MUSIC(2),
    /** Revival owns its complete authoritative channel. Enum wire order remains unchanged. */
    REVIVAL(-1);

    private final int priority;

    Source(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
