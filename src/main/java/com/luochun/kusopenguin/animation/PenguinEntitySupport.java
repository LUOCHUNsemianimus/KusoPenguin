package com.luochun.kusopenguin.animation;

import java.util.Set;
import net.minecraft.world.entity.EntityType;

/**
 * Single common source for the vanilla entity types allowed to become penguins.
 *
 * <p>{@link #isSupportedType} deliberately keeps its original meaning: the upright, action-capable
 * set used by server kick/taunt/death eligibility. Prone replacements are a separate render-only
 * set, so enabling their rendering can never start the humanoid action clips on a cow or spider.
 */
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
    /** Listed adults rendered as a horizontal, sliding penguin body with their native head. */
    private static final Set<EntityType<?>> PRONE_TYPES = Set.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.CAT, EntityType.WOLF,
            EntityType.SPIDER, EntityType.CAVE_SPIDER
    );

    private PenguinEntitySupport() {
    }

    /** Upright action-capable contract; unchanged so server eligibility stays byte-for-byte. */
    public static boolean isSupportedType(EntityType<?> type) {
        return SUPPORTED_TYPES.contains(type);
    }

    /** Render-only prone contract. These types are intentionally absent from the set above. */
    public static boolean isProneType(EntityType<?> type) {
        return PRONE_TYPES.contains(type);
    }
}
