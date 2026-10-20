package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * One shared gate for every draw/filter path. Unsupported states deliberately keep the complete
 * vanilla renderer instead of mixing the original head with a penguin body.
 */
public final class PenguinRenderSupport {
    private PenguinRenderSupport() {
    }

    public static boolean isSupportedType(EntityType<?> type) {
        return PenguinEntitySupport.isSupportedType(type);
    }

    public static boolean shouldReplace(LivingEntity entity) {
        if(PenguinDeathClient.rendering(entity))return true;
        if (!isSupportedType(entity.getType())) {
            return false;
        }
        if (entity.isBaby()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.isCrouching()
                || entity.isPassenger()
                || entity.isSwimming()
                || entity.isFallFlying()
                || entity.isSpectator()) {
            return false;
        }
        return entity.hasEffect(ModEffects.PENGUIN);
    }
}
