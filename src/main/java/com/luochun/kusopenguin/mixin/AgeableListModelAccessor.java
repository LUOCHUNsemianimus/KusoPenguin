package com.luochun.kusopenguin.mixin;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Shared head lookup for quadruped-family models. The protected {@code headParts()} contract is
 * used instead of guessing child names or using reflection, so cow horns, pig snouts, wolf
 * {@code real_head} children and cat noses all stay attached to the native head.
 */
@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
    @Invoker("headParts")
    Iterable<ModelPart> kusoPenguin$headParts();
}
