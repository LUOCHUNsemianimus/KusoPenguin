package com.luochun.kusopenguin.revival;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class RevivalNetwork {
    private RevivalNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("8");
        registrar.playToServer(RevivalPackets.Request.TYPE, RevivalPackets.Request.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RevivalManager.request(player, payload.target());
                    }
                }));
        registrar.playToServer(RevivalPackets.Control.TYPE, RevivalPackets.Control.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RevivalManager.control(player, payload.token(), payload.hand(),
                                payload.action());
                    }
                }));
        registrar.playToClient(RevivalPackets.State.TYPE, RevivalPackets.State.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> RevivalClientHooks.receive(payload)));
    }
}
