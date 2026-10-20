package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.client.PenguinPoseClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class PenguinPoseNetwork {
    private PenguinPoseNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("3");
        registrar.playToServer(PenguinKickRequest.TYPE,PenguinKickRequest.STREAM_CODEC,
            (payload,context)->context.enqueueWork(()->PenguinKick.tryStart(context.player())));
        registrar.playToClient(PenguinKickMotion.TYPE,PenguinKickMotion.STREAM_CODEC,
            (payload,context)->context.enqueueWork(()->com.luochun.kusopenguin.client.PenguinKickInput.receive(payload)));
        registrar.playToClient(PenguinPosePayload.TYPE,PenguinPosePayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->PenguinPoseClient.receive(payload)));
    }
}
