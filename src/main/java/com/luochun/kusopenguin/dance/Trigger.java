package com.luochun.kusopenguin.dance;

/** The four persistent preference channels. The set is intentionally closed. */
public enum Trigger {
    MUSIC(DanceLibrary.BUILTIN_CYCLE),
    REVIVAL(DanceLibrary.BUILTIN_POLE),
    MAID_MUSIC(DanceLibrary.BUILTIN_CYCLE),
    /** Jukebox animals that are neither a player nor an exact Touhou Little Maid. */
    ANIMAL_MUSIC(DanceLibrary.BUILTIN_CYCLE);

    private final String defaultClip;

    Trigger(String defaultClip) {
        this.defaultClip = defaultClip;
    }

    /** Builtin id used when the stored selection is empty or no longer valid. */
    public String defaultClip() {
        return defaultClip;
    }
}
