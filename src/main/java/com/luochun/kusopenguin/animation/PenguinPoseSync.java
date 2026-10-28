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
    public static final long PLAYER_KICK_COOLDOWN=60L;
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
        return startSequence(entity,action)!=0L;
    }

    /**
     * Starts a flying kick and returns its authoritative sequence, or 0 when rejected. A Flight
     * stores this sequence so only this exact kick can later be cancelled by its owner.
     */
    static long startKick(LivingEntity entity){
        return startSequence(entity,PenguinAction.FLYING_KICK);
    }

    /**
     * Stops one exact, still-active FLYING_KICK. A stale sequence, a newer kick, TAUNT and DEATH
     * are all left untouched. The kick cooldown is deliberately preserved.
     */
    static boolean cancelKick(LivingEntity entity,long sequence){
        if(!(entity.level() instanceof ServerLevel level))return false;
        State current=STATES.get(entity);
        if(current==null||current.action()!=PenguinAction.FLYING_KICK
            ||current.sequence()!=sequence)return false;
        if(!current.activeAt(level.getGameTime())){STATES.remove(entity);return false;}
        STATES.remove(entity);
        // The stop packet carries a strictly higher sequence so clients accept it ahead of the
        // start packet even when both arrive in the same connection flush.
        State stopped=new State(PenguinAction.FLYING_KICK,SEQUENCE.incrementAndGet(),current.started());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,stopped,false));
        return true;
    }

    private static long startSequence(LivingEntity entity,PenguinAction action){
        // Visual-only boss: never start taunt/kick/death state even when the effect tag allows the
        // potion. Effect broadcasting/removal remains the untouched native path.
        if(PenguinEntitySupport.isNativeAnimatedBoss(entity.getType()))return 0L;
        if(!(entity.level() instanceof ServerLevel level))return 0L;
        if(!entity.hasEffect(ModEffects.PENGUIN))return 0L;
        long now=level.getGameTime();
        State current=STATES.get(entity);
        if(current!=null&&!current.activeAt(now)){STATES.remove(entity);current=null;}
        if(action==PenguinAction.FLYING_KICK&&onKickCooldown(entity,now))return 0L;
        if(current!=null&&action.priority()<=current.action().priority())return 0L;
        State next=new State(action,SEQUENCE.incrementAndGet(),now);
        STATES.put(entity,next);
        if(action==PenguinAction.FLYING_KICK)KICK_COOLDOWNS.put(entity,now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,packet(entity,next,true));
        return next.sequence();
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
