package com.luochun.kusopenguin.client.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.vocals.VocalClientHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client lifecycle hook; the screen remains absent until the next UI step. */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD)
public final class VocalClientSetup {
    private VocalClientSetup() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VocalClientHooks.install(VocalClient.INSTANCE);
            VocalClient.installStateListener(VocalScreens::accept);
            MicrophonePose.register();
        });
    }
}
