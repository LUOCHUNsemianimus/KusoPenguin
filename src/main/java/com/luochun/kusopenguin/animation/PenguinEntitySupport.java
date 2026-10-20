package com.luochun.kusopenguin.animation;

import java.util.Set;
import net.minecraft.world.entity.EntityType;

/** Single common source for the vanilla entity types allowed to become penguins. */
public final class PenguinEntitySupport {
    private static final Set<EntityType<?>> SUPPORTED_TYPES = Set.of(
            EntityType.PLAYER,
            EntityType.SKELETON,
            EntityType.ZOMBIE,
            EntityType.VILLAGER,
            EntityType.PIGLIN,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIE_VILLAGER, EntityType.IRON_GOLEM, EntityType.WARDEN
    );

    private PenguinEntitySupport() {
    }

    public static boolean isSupportedType(EntityType<?> type) {
        return SUPPORTED_TYPES.contains(type);
    }
}
