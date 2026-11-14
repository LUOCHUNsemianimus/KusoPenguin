package com.luochun.kusopenguin.music;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server config, synchronized by NeoForge; density never changes player hitboxes. */
public final class DanceSettings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue RADIUS;
    public static final ModConfigSpec.DoubleValue SPACING;
    public static final ModConfigSpec.BooleanValue UNRESTRICTED_MAID_MUSIC_DANCE;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("dance");
        RADIUS = builder.comment("Dance selection radius around a playing custom disc jukebox, in blocks.")
                .defineInRange("radius", 12.0, 4.0, 32.0);
        SPACING = builder.comment("Minimum gap between reserved native collision boxes, in blocks.",
                "Penguin visuals shrink, but native collision boxes keep their original size.")
                .defineInRange("spacing", .5, .1, 3.0);
        UNRESTRICTED_MAID_MUSIC_DANCE = builder.comment(
                "When false, a maid joins the maid-music dance only while its task is idle and its",
                "native schedule detail is IDLE (休息); work or day/night schedule changes end the dance.",
                "When true, the previous unrestricted behaviour skips both the task and schedule checks.")
                .define("unrestrictedMaidMusicDance", false);
        builder.pop();
        SPEC = builder.build();
    }
    private DanceSettings() {}
}
