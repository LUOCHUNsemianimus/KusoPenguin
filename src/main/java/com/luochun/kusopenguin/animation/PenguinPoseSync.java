package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME)
public final class PenguinPoseSync {
    private record State(long sequence,long started){}
    private static final Map<LivingEntity,State> STATES=new WeakHashMap<>();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private PenguinPoseSync(){}
    public static void started(LivingEntity entity){
        if(!(entity.level() instanceof ServerLevel level))return;
        State s=new State(SEQUENCE.incrementAndGet(),level.getGameTime());STATES.put(entity,s);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,s,true));
    }
    public static void removed(LivingEntity entity){
        if(!(entity.level() instanceof ServerLevel level))return;
        State s=new State(SEQUENCE.incrementAndGet(),level.getGameTime());STATES.remove(entity);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,s,false));
    }
    public static void snapshot(LivingEntity entity,ServerPlayer player){
        State s=STATES.get(entity);
        if(s!=null&&entity.hasEffect(ModEffects.PENGUIN))PacketDistributor.sendToPlayer(player,packet(entity,s,true));
    }
    private static PenguinPosePayload packet(LivingEntity e,State s,boolean playing){
        return new PenguinPosePayload(e.level().dimension().location(),e.getId(),e.getUUID(),s.sequence(),s.started(),playing);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof LivingEntity e)STATES.remove(e);
    }
}
