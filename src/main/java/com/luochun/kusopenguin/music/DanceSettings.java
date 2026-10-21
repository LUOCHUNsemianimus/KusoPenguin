package com.luochun.kusopenguin.music;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server config, synchronized by NeoForge; density never changes player hitboxes. */
public final class DanceSettings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue RADIUS;
    public static final ModConfigSpec.DoubleValue SPACING;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("dance");
        RADIUS = builder.comment("Dance selection radius around a playing custom disc jukebox, in blocks.")
                .defineInRange("radius", 12.0, 4.0, 32.0);
        SPACING = builder.comment("Minimum gap between reserved native collision boxes, in blocks.",
                "Penguin visuals shrink, but native collision boxes keep their original size.")
                .defineInRange("spacing", .5, .1, 3.0);
        builder.pop();
        SPEC = builder.build();
    }
    private DanceSettings() {}
}
