package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;

/** Typed disc packet registration. Client handlers only enqueue the common hook bridge. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DiscNetwork {
    private DiscNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("5");

        registrar.playToServer(DiscPackets.Request.TYPE, DiscPackets.Request.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    DiscServer.receive(player, payload);
                }
            }));
        registrar.playBidirectional(DiscPackets.Chunk.TYPE, DiscPackets.Chunk.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                (payload, context) -> context.enqueueWork(() -> DiscClientHooks.receive(payload)),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DiscServer.receive(player, payload);
                    }
                })));
        registrar.playToClient(DiscPackets.Reply.TYPE, DiscPackets.Reply.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DiscClientHooks.receive(payload)));
        registrar.playToClient(DiscPackets.Catalog.TYPE, DiscPackets.Catalog.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DiscClientHooks.receive(payload)));
        registrar.playToClient(DiscPackets.Playback.TYPE, DiscPackets.Playback.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DiscClientHooks.receive(payload)));
        registrar.playToClient(DiscPackets.Dance.TYPE, DiscPackets.Dance.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> DiscClientHooks.receive(payload)));
    }
}
