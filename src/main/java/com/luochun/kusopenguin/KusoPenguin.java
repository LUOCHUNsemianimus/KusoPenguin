package com.luochun.kusopenguin;

import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.registry.ModPotions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(KusoPenguin.MODID)
public final class KusoPenguin {
    public static final String MODID = "kuso_penguin";

    public KusoPenguin(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.register(modEventBus);
        ModPotions.register(modEventBus);
    }
}
