package com.luochun.kusopenguin.registry;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * The penguin brewing chain, added on top of the vanilla recipes.
 *
 * <p>Water + nether wart -&gt; awkward stays vanilla. Vanilla keeps its own water + fermented
 * spider eye -&gt; weakness mix; the penguin chain deliberately uses an effect-free custom base
 * instead, so a weakness potion is never a shortcut to the penguin effect.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID)
public final class PenguinBrewing {
    private PenguinBrewing() {
    }

    @SubscribeEvent
    public static void registerRecipes(RegisterBrewingRecipesEvent event) {
        // Awkward -> penguin_base: effect-free custom intermediate, replacing the old
        // awkward -> weakness shortcut. Vanilla water -> weakness remains untouched.
        event.getBuilder().addMix(Potions.AWKWARD, Items.FERMENTED_SPIDER_EYE, ModPotions.PENGUIN_BASE);
        // Penguin base -> penguin (3 minutes, matching the existing potion definition).
        event.getBuilder().addMix(ModPotions.PENGUIN_BASE, Items.FEATHER, ModPotions.PENGUIN);
        // Penguin + redstone -> long penguin (8 minutes).
        event.getBuilder().addMix(ModPotions.PENGUIN, Items.REDSTONE, ModPotions.LONG_PENGUIN);
    }
}
