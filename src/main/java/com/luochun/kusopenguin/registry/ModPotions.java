package com.luochun.kusopenguin.registry;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPotions {
    private static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, KusoPenguin.MODID);

    public static final DeferredHolder<Potion, Potion> PENGUIN =
            POTIONS.register("penguin", () -> new Potion(
                    new MobEffectInstance(ModEffects.PENGUIN, 3600, 0, false, true, true)));

    private ModPotions() {
    }

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
