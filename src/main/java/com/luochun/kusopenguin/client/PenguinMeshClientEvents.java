package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Registration for the experimental mesh preview; all rendering still goes through the body layer. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class PenguinMeshClientEvents {
    private PenguinMeshClientEvents() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PenguinMeshPreview.toggleKey());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PenguinMeshModel.reloadListener());
    }
}
