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

    /** Effect-free brewing intermediate; the custom base never grants a vanilla weakness effect. */
    public static final DeferredHolder<Potion, Potion> PENGUIN_BASE =
            POTIONS.register("penguin_base", () -> new Potion());

    /** Redstone-extended penguin potion: 9600 ticks (8 minutes). */
    public static final DeferredHolder<Potion, Potion> LONG_PENGUIN =
            POTIONS.register("long_penguin", () -> new Potion(
                    new MobEffectInstance(ModEffects.PENGUIN, 9600, 0, false, true, true)));

    private ModPotions() {
    }

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
