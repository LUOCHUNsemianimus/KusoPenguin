package com.luochun.kusopenguin.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Read-only access to Immersive Melodies' native loop counter and effective start time.
 *
 * <p>The target is a string so absent IM installations skip this pseudo mixin during loading.
 * These methods expose no IM type in their signatures; only the lazy vocal timeline Impl casts the
 * progress object to this interface.</p>
 */
@Pseudo
@Mixin(targets = "immersive_melodies.client.MelodyProgress", remap = false)
public interface MelodyProgressAccessor {
    @Accessor(value = "loop", remap = false)
    long kuso$loop();

    @Invoker(value = "getStartTime", remap = false)
    long kuso$getStartTime();
}
