package com.luochun.kusopenguin.revival;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Optional-mod ids used without loading any optional API class. */
public final class RevivalIds {
    public static final ResourceLocation MAID = id("maid");
    public static final ResourceLocation TOMBSTONE = id("tombstone");
    public static final ResourceLocation MAID_INFO_ID = id("maid");

    private RevivalIds() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("touhou_little_maid", path);
    }

    public static boolean isTombstone(Entity entity) {
        return entity != null && TOMBSTONE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static boolean isMaidType(Entity entity) {
        return entity != null && MAID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static boolean isMaidEntityId(ResourceLocation id) {
        return MAID.equals(id);
    }
}
