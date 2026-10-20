package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * One shared gate for every draw/filter path. Unsupported states deliberately keep the complete
 * vanilla renderer instead of mixing the original head with a penguin body.
 *
 * <p>Adult players keep the penguin body while crouching, riding or swimming because
 * {@link PenguinPlayerPosture} now fits those native poses. Other mobs that were excluded in those
 * states keep their previous complete vanilla renderer.
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
                || entity.isFallFlying()
                || entity.isSpectator()) {
            return false;
        }
        if (!(entity instanceof Player)
                && (entity.isCrouching() || entity.isPassenger() || entity.isSwimming())) {
            return false;
        }
        return entity.hasEffect(ModEffects.PENGUIN);
    }
}
