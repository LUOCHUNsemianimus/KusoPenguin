package com.luochun.kusopenguin.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Typed vocal packet registration; client work is delegated to the common hook bridge. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class VocalNetwork {
    private VocalNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(VocalPackets.Request.TYPE, VocalPackets.Request.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    VocalServer.receive(player, payload);
                }
            }));
        registrar.playToClient(VocalPackets.State.TYPE, VocalPackets.State.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> VocalClientHooks.receive(payload)));
    }
}
