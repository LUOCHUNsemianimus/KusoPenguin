package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME)
public final class PenguinPoseSync {
    private static final long PLAYER_KICK_COOLDOWN=60L;
    private static final long MOB_KICK_COOLDOWN=80L;
    private record State(PenguinAction action,long sequence,long started){
        boolean activeAt(long gameTime){return gameTime-started<action.durationTicks();}
    }
    private static final Map<LivingEntity,State> STATES=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> KICK_COOLDOWNS=new WeakHashMap<>();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private PenguinPoseSync(){}

    /** Compatibility entry point used when the penguin effect is added. */
    public static void started(LivingEntity entity){
        start(entity,PenguinAction.TAUNT);
    }

    /** Unified server state boundary for every action. */
    public static boolean start(LivingEntity entity,PenguinAction action){
        if(!(entity.level() instanceof ServerLevel level))return false;
        if(!entity.hasEffect(ModEffects.PENGUIN))return false;
        long now=level.getGameTime();
        State current=STATES.get(entity);
        if(current!=null&&!current.activeAt(now)){STATES.remove(entity);current=null;}
        if(action==PenguinAction.FLYING_KICK&&onKickCooldown(entity,now))return false;
        if(current!=null&&action.priority()<=current.action().priority())return false;
        State next=new State(action,SEQUENCE.incrementAndGet(),now);
        STATES.put(entity,next);
        if(action==PenguinAction.FLYING_KICK)KICK_COOLDOWNS.put(entity,now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,next,true));
        return true;
    }

    public static void removed(LivingEntity entity){
        if(!(entity.level() instanceof ServerLevel level))return;
        State old=STATES.get(entity);
        if(old!=null&&old.action()==PenguinAction.DEATH)return;
        STATES.remove(entity);
        PenguinAction action=old==null?PenguinAction.TAUNT:old.action();
        State s=new State(action,SEQUENCE.incrementAndGet(),level.getGameTime());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,s,false));
    }

    public static void snapshot(LivingEntity entity,ServerPlayer player){
        State s=STATES.get(entity);
        if(s==null||!entity.hasEffect(ModEffects.PENGUIN))return;
        long now=entity.level().getGameTime();
        if(!s.activeAt(now))return;
        PacketDistributor.sendToPlayer(player,packet(entity,s,true));
    }

    private static boolean onKickCooldown(LivingEntity entity,long now){
        Long last=KICK_COOLDOWNS.get(entity);
        long cooldown=entity instanceof Player?PLAYER_KICK_COOLDOWN:MOB_KICK_COOLDOWN;
        return last!=null&&now<last+cooldown;
    }

    private static PenguinPosePayload packet(LivingEntity e,State s,boolean playing){
        return new PenguinPosePayload(e.level().dimension().location(),e.getId(),e.getUUID(),
                s.sequence(),s.started(),playing,s.action());
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof LivingEntity e){
            STATES.remove(e);
            KICK_COOLDOWNS.remove(e);
        }
    }
}
