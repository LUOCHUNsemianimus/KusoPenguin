package com.luochun.kusopenguin.revival;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional-mod ids used without loading any optional API class. */
public final class RevivalIds {
    public static final ResourceLocation MAID = id("maid");
    public static final ResourceLocation FILM = id("film");
    public static final ResourceLocation MAID_INFO_ID = id("maid");

    private RevivalIds() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("touhou_little_maid", path);
    }

    public static boolean isFilmItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && FILM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isMaidEntityId(ResourceLocation id) {
        return MAID.equals(id);
    }
}
