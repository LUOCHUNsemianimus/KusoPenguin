package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.music.DiscClientHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DiscClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiscClientHooks.install(new DiscClient()));
    }
}
