package com.luochun.kusopenguin.dance;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;

/**
 * Typed registration for the independent dance payload family. The packet ids, action set and
 * chunk budget are separate from the disc/music transport.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DanceNetwork {
    private DanceNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");

        registrar.playToServer(DancePackets.Request.TYPE, DancePackets.Request.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    DanceServer.receive(player, payload);
                }
            }));
        registrar.playBidirectional(DancePackets.Chunk.TYPE, DancePackets.Chunk.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                (payload, context) -> context.enqueueWork(() -> DanceClientHooks.receive(payload)),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DanceServer.receive(player, payload);
                    }
                })));
        registrar.playToServer(DancePackets.Control.TYPE, DancePackets.Control.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    DanceServer.receive(player, payload);
                }
            }));
        registrar.playToClient(DancePackets.Reply.TYPE, DancePackets.Reply.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DanceClientHooks.receive(payload)));
        registrar.playToClient(DancePackets.Catalog.TYPE, DancePackets.Catalog.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DanceClientHooks.receive(payload)));
        registrar.playToClient(DancePackets.Playback.TYPE, DancePackets.Playback.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DanceClientHooks.receive(payload)));
        // Optional so a client without this exact marker still connects; the server also checks the
        // channel before sending, and the marker carries no client-supplied authority.
        registrar.optional().playToClient(DancePackets.FakeTargetMarker.TYPE,
            DancePackets.FakeTargetMarker.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DanceClientHooks.receive(payload)));
    }
}
