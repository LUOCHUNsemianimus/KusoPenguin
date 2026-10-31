package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.dance.DanceClientHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Installs the dance listener at client setup, not from a payload/render callback. */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DanceClientSetup {
    private DanceClientSetup() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DanceClientHooks.install(new DanceClient()));
    }
}
