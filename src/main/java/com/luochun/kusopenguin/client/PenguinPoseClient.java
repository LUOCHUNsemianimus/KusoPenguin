package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinAction;
import com.luochun.kusopenguin.animation.PenguinPosePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME,value=Dist.CLIENT)
public final class PenguinPoseClient {
    private record Key(ResourceLocation dimension,UUID uuid){}
    private record Entry(int entityId,PenguinAction action,long sequence,long started,boolean playing,long receivedNanos){}
    private static final Map<Key,Entry> ENTRIES=new HashMap<>();
    private static int cleanupTick;
    private PenguinPoseClient(){}
    public static void receive(PenguinPosePayload p){
        Key k=new Key(p.dimension(),p.uuid());Entry old=ENTRIES.get(k);
        if(old!=null&&p.sequence()<old.sequence())return;
        ENTRIES.put(k,new Entry(p.entityId(),p.action(),p.sequence(),p.startedGameTime(),p.playing(),System.nanoTime()));
        PenguinDeathClient.receive(p);
    }
    public static PenguinAction action(LivingEntity e){
        Entry s=entry(e);
        return s==null?null:s.action();
    }
    public static float age(LivingEntity e,PenguinAction action,float partial){
        Entry s=entry(e);
        if(s==null||s.action()!=action)return -1;
        return Math.max(0,((e.level().getGameTime()-s.started())+partial)/20.0F);
    }
    /** Compatibility query for the Taunt clip. */
    public static float age(LivingEntity e,float partial){
        return age(e,PenguinAction.TAUNT,partial);
    }
    private static Entry entry(LivingEntity e){
        Entry s=ENTRIES.get(new Key(e.level().dimension().location(),e.getUUID()));
        if(s==null||!s.playing()||s.entityId()!=e.getId())return null;
        return s;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){ENTRIES.clear();}
    @SubscribeEvent public static void cleanup(ClientTickEvent.Post event){
        if(++cleanupTick%200==0){long threshold=System.nanoTime()-120_000_000_000L;ENTRIES.values().removeIf(s->s.receivedNanos()<threshold);}
    }
}
