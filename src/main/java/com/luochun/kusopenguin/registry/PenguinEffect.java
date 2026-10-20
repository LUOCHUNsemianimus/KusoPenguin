package com.luochun.kusopenguin.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * MobEffect's constructor is protected, so the penguin effect needs a concrete subclass.
 */
public final class PenguinEffect extends MobEffect {
    public PenguinEffect() {
        super(MobEffectCategory.NEUTRAL, 0x24405F);
    }
}
