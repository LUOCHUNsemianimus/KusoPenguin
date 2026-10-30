package com.luochun.kusopenguin;

import com.luochun.kusopenguin.music.MusicItems;
import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.revival.RevivalEntities;
import com.luochun.kusopenguin.registry.ModPotions;
import com.luochun.kusopenguin.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(KusoPenguin.MODID)
public final class KusoPenguin {
    public static final String MODID = "kuso_penguin";

    public KusoPenguin(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.register(modEventBus);
        ModPotions.register(modEventBus);
        MusicItems.register(modEventBus);
        ModSounds.register(modEventBus);
        RevivalEntities.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                com.luochun.kusopenguin.music.DanceSettings.SPEC);
    }
}
