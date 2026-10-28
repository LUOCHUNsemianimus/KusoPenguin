package com.luochun.kusopenguin.animation;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Single common source for the vanilla entity types allowed to become penguins.
 *
 * <p>{@link #isSupportedType} deliberately keeps its original meaning: the upright, action-capable
 * set used by server kick/taunt/death eligibility. Prone replacements are a separate render-only
 * set, so enabling their rendering can never start the humanoid action clips on a cow or spider.
 *
 * <p>The optional maid type is matched by registry id only. This predicate never loads a maid
 * class, so it is safe on a standalone game and on a dedicated server.
 */
public final class PenguinEntitySupport {
    private static final ResourceLocation MAID_ID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid");
    /**
     * Exact visual-only Cataclysm boss id. It is intentionally not added to either action set:
     * the boss keeps its native AI, attack, grab and death state, and only its rendered body is
     * replaced by the optional compat bridge.
     */
    private static final ResourceLocation MALEDICTUS_ID =
            ResourceLocation.fromNamespaceAndPath("cataclysm", "maledictus");
    /**
     * Exact visual-only Scylla id. Like Maledictus it stays outside both action sets; the native
     * renderer/AI/death/sockets remain in charge and only the body pass is replaced.
     */
    private static final ResourceLocation SCYLLA_ID =
            ResourceLocation.fromNamespaceAndPath("cataclysm", "scylla");
    private static final ResourceLocation IGNIS_ID =
            ResourceLocation.fromNamespaceAndPath("cataclysm", "ignis");
    /** Exact visual-only Ender Guardian id; separate from both action and prone allowlists. */
    private static final ResourceLocation ENDER_GUARDIAN_ID =
            ResourceLocation.fromNamespaceAndPath("cataclysm", "ender_guardian");
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

    /** Upright action-capable contract plus the optional maid action/pose eligibility. */
    public static boolean isSupportedType(EntityType<?> type) {
        return SUPPORTED_TYPES.contains(type) || isMaid(type);
    }

    /** Exact optional maid registry id, resolved without touching a maid API class. */
    public static boolean isMaid(EntityType<?> type) {
        return type != null && MAID_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /**
     * Exact optional Maledictus id for the visual-only render bridge. This predicate never loads a
     * Cataclysm class and is deliberately separate from both {@link #isSupportedType} and
     * {@link #isProneType}, so no Kuso action/death/dance eligibility is widened.
     */
    public static boolean isMaledictus(EntityType<?> type) {
        return type != null && MALEDICTUS_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /** Exact optional Scylla id, kept separate from the action/prone allowlists. */
    public static boolean isScylla(EntityType<?> type) {
        return type != null && SCYLLA_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /** Exact floating visual-only boss; never widens action or prone eligibility. */
    public static boolean isIgnis(EntityType<?> type) {
        return type != null && IGNIS_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /**
     * Union used by action exclusion and boss diagnostics. Each bridge gates on its own exact
     * model class, so this union cannot make one boss accept the other's model.
     */
    public static boolean isNativeAnimatedBoss(EntityType<?> type) {
        return isMaledictus(type) || isScylla(type) || isIgnis(type)
                || isEnderGuardian(type);
    }

    /** Exact optional Ender Guardian id, kept out of the action/prone allowlists. */
    public static boolean isEnderGuardian(EntityType<?> type) {
        return type != null && ENDER_GUARDIAN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /** Render-only prone contract. These types are intentionally absent from the set above. */
    public static boolean isProneType(EntityType<?> type) {
        return PRONE_TYPES.contains(type);
    }
}
