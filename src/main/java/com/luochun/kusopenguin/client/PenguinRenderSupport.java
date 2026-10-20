package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.registry.ModEffects;
import java.util.Set;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * One shared gate for every draw/filter path. Unsupported states deliberately keep the complete
 * vanilla renderer instead of mixing the original head with a penguin body.
 */
public final class PenguinRenderSupport {
    private static final Set<EntityType<?>> SUPPORTED_TYPES = Set.of(
            EntityType.PLAYER,
            EntityType.SKELETON,
            EntityType.ZOMBIE,
            EntityType.VILLAGER,
            EntityType.PIGLIN,
            EntityType.ZOMBIFIED_PIGLIN
    );

    private PenguinRenderSupport() {
    }

    public static boolean isSupportedType(EntityType<?> type) {
        return SUPPORTED_TYPES.contains(type);
    }

    public static boolean shouldReplace(LivingEntity entity) {
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
