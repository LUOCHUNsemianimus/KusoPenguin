package com.luochun.kusopenguin.animation;

import com.google.common.collect.MapMaker;
import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.compat.MaidKickCompat;
import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.registry.ModSounds;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server owns activation, cooldown and hits. Travel uses vanilla collision resolution. */
@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME)
public final class PenguinKick {
    public static final int TRAVEL_TICKS=12;
    /** Vanilla's own ADD_MULTIPLIED_TOTAL modifier id on player movement speed. */
    private static final ResourceLocation SPRINTING_SPEED_ID=ResourceLocation.fromNamespaceAndPath("minecraft","sprinting");
    /** Headroom beyond the approved horizontal distance for the bounded-origin check. */
    private static final double ORIGIN_SLACK=2;
    /** Headroom beyond the largest approved per-tick step for the external-displacement check. */
    private static final double STEP_SLACK=.5;
    /** Position or velocity below this is treated as settled rather than a new external impulse. */
    private static final double EXTERNAL_DISPLACEMENT_SQR=1.0E-4;
    private static final double EXTERNAL_VELOCITY_SQR=1.0E-6;
    // Weak keys compare by object identity here, which keeps the integrated server's ServerPlayer
    // and the client's separate LocalPlayer (same numeric id) on their own Flight entries.
    private static final Map<LivingEntity,Flight> FLIGHTS=new MapMaker().weakKeys().makeMap();
    private static final ThreadLocal<Mob> MOB_STRIKE=new ThreadLocal<>();
    private static final ThreadLocal<Player> ATTACKER=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HIT=ThreadLocal.withInitial(()->false);
    private static final class Flight {
        final Vec3 origin,dir; final double distance,height; final int started;
        final double originBoundSqr,maxStepSqr;
        /** Identity of the PoseSync FLYING_KICK state that owns this flight. */
        final long sequence;
        int step; boolean ended; final LivingEntity target; Vec3 previous; final Set<UUID> hit=new HashSet<>();
        /** Position after the previous manual travel step; kept apart from previous (hit sweep). */
        Vec3 lastTravelPosition;
        /** Trajectory goal at the previous manual travel step. */
        Vec3 lastTravelGoal;
        Flight(LivingEntity e,Vec3 origin,Vec3 dir,double distance,double height,long sequence){
            this.origin=origin;this.previous=e.position();this.dir=dir;this.distance=distance;this.height=height;this.started=e.tickCount;this.target=e instanceof Mob mob?mob.getTarget():null;
            this.sequence=sequence;
            this.lastTravelPosition=e.position();
            this.lastTravelGoal=origin;
            // Both safety bounds are derived from the server-approved distance so a legitimate
            // boosted kick is not aborted; unboosted values stay at the old 6 / 1.5 scale.
            double originBound=distance+ORIGIN_SLACK;
            this.originBoundSqr=originBound*originBound;
            double maxStep=distance/TRAVEL_TICKS+4*height/TRAVEL_TICKS+STEP_SLACK;
            this.maxStepSqr=maxStep*maxStep;
        }
    }
    private PenguinKick(){}
    public static boolean active(LivingEntity e){return FLIGHTS.containsKey(e);}
    public static boolean dispatching(Player p){return ATTACKER.get()==p;}
    public static void confirmedHit(Player p,boolean hit){if(dispatching(p)&&hit)HIT.set(true);}
    /** Native weapon/enchantment damage, before ordinary armor and resistance handling. */
    public static float damage(Player p,float amount){
        if(!dispatching(p))return amount;
        var settings=PenguinKickSettings.get(((ServerLevel)p.level()).getServer()).values(p.getUUID());
        return (float)(amount*settings.damageMultiplier());
    }
    public static boolean tryStart(LivingEntity e){
        // Maid is eligible only through the exact optional melee task gate; the ordinary maid
        // attack override stays blocked by the mixin unless this kick owns the dispatch.
        if(PenguinEntitySupport.isMaid(e.getType())&&!MaidKickCompat.isEligibleMaid(e))return false;
        if(e instanceof Mob mob && com.luochun.kusopenguin.music.DanceManager.pausesAI(mob))return false;
        if(!(e.level() instanceof ServerLevel)||!e.isAlive()||!e.hasEffect(ModEffects.PENGUIN)
            ||!PenguinEntitySupport.isSupportedType(e.getType())||!e.onGround()||e.isPassenger()
            ||e.isSwimming()||e.isFallFlying()||e.isSpectator()||e.isBaby()||active(e))return false;
        if(e instanceof Player p&&(!p.isSprinting()||p.isCrouching()))return false;
        long sequence=PenguinPoseSync.startKick(e);
        if(sequence==0L)return false;
        Vec3 look=e instanceof Mob m&&m.getTarget()!=null?m.getTarget().position().subtract(e.position()):e.getLookAngle();Vec3 dir=new Vec3(look.x,0,look.z).normalize();
        // An eligible maid uses the same approved player-sized leap; mob speed scaling still applies.
        boolean playerLike=e instanceof Player||PenguinEntitySupport.isMaid(e.getType());
        double baseDistance=playerLike?4:independentMob(e)?3:1.5,height=playerLike?1:.75;
        double distance=baseDistance*speedScale(e);
        Flight flight=new Flight(e,e.position(),dir,distance,height,sequence);
        FLIGHTS.put(e,flight);
        ModSounds.playKick(e);
        // The confirmed start intentionally replaces ordinary pre-kick velocity.
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance=0;
        if(e instanceof ServerPlayer p)PacketDistributor.sendToPlayer(p,startMotion(e,flight));
        return true;
    }
    /**
     * Movement-speed multiplier relative to the entity type's default, with vanilla sprinting
     * removed so ordinary sprint does not inflate the approved travel. Sampled once at activation.
     */
    @SuppressWarnings("unchecked")
    static double speedScale(LivingEntity e){
        AttributeInstance speed=e.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed==null)return 1;
        AttributeSupplier defaults=DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>)e.getType());
        if(!defaults.hasAttribute(Attributes.MOVEMENT_SPEED))return 1;
        double baseline=defaults.getValue(Attributes.MOVEMENT_SPEED);
        if(!(baseline>1.0E-9))return 1;
        // Native AttributeInstance.calculateValue multiplies each total modifier in sequence.
        // Re-evaluate without sprint instead of dividing a sum or temporarily mutating attributes.
        double base=speed.getBaseValue();
        for(AttributeModifier mod:speed.getModifiers())
            if(mod.operation()==AttributeModifier.Operation.ADD_VALUE)base+=mod.amount();
        double withoutSprint=base;
        for(AttributeModifier mod:speed.getModifiers())
            if(mod.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)withoutSprint+=base*mod.amount();
        for(AttributeModifier mod:speed.getModifiers())
            if(mod.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL&&!SPRINTING_SPEED_ID.equals(mod.id()))
                withoutSprint*=1+mod.amount();
        withoutSprint=speed.getAttribute().value().sanitizeValue(withoutSprint);
        double scale=withoutSprint/baseline;
        return Double.isFinite(scale)?Math.max(0,scale):1;
    }
    /**
     * Client-side mirror of a confirmed server kick. The sequence must come from the start packet;
     * there is no constructor or default that invents one.
     */
    public static void startLocal(Player p,Vec3 origin,Vec3 direction,double distance,long sequence){
        if(!p.level().isClientSide()||!p.isLocalPlayer())return;
        Flight old=FLIGHTS.get(p);
        if(old!=null&&old.sequence>=sequence)return;
        FLIGHTS.put(p,new Flight(p,origin,direction,distance,1,sequence));
        p.setDeltaMovement(Vec3.ZERO);
    }
    /** Server-authoritative stop for the local mirror; never notifies the server back. */
    public static void stopLocal(Player p,long sequence){
        Flight f=FLIGHTS.get(p);
        if(f==null||f.sequence!=sequence)return;
        FLIGHTS.remove(p);
        // The manual step already cleared its own velocity. Any remaining delta here can be a real
        // incoming impulse (for example knockback), so the stop must not erase it.
    }
    /** The server only accepts a cancel for the sender's exact current flight. */
    static void cancelFromClient(Player player,long sequence){
        Flight f=FLIGHTS.get(player);
        if(f==null||f.sequence!=sequence)return;
        interruptFlight(player,f,false);
    }
    /** Called instead of ordinary travel only for the local player or a server-controlled mob. */
    public static boolean travel(LivingEntity e){
        Flight f=FLIGHTS.get(e);
        if(f==null||e instanceof ServerPlayer||e.level().isClientSide()&&!(e instanceof Player p&&p.isLocalPlayer()))return false;
        // An impulse received between controlled steps owns the entity's motion now. Stop at once
        // and keep that impulse instead of overwriting it with the next trajectory increment.
        if(f.step>0&&externalImpulse(e,f)){interruptFlight(e,f,true);return false;}
        if(!valid(e,f)){interruptFlight(e,f,false);return false;}
        if(f.step>=TRAVEL_TICKS){completeTravel(e,f);return false;}
        double t=(++f.step)/(double)TRAVEL_TICKS;
        Vec3 goal=trajectory(f,t);
        // Incremental step from the previous goal, not absolute catch-up from the current position.
        Vec3 delta=goal.subtract(f.lastTravelGoal);
        if(delta.lengthSqr()>f.maxStepSqr){interruptFlight(e,f,true);return false;}
        e.setDeltaMovement(delta);e.move(MoverType.SELF,delta);e.fallDistance=0;
        f.lastTravelPosition=e.position();
        f.lastTravelGoal=goal;
        // Only the kick-owned velocity is cleared, so ordinary travel resumes without stale motion.
        e.setDeltaMovement(Vec3.ZERO);
        if(e.horizontalCollision||e.verticalCollision&&t<.5){
            f.ended=true;
            // A server mob keeps its flight through this post-tick so the collided sweep segment
            // can still hit; the pose is cancelled now and tick() removes the flight afterwards.
            interruptFlight(e,f,false,true);
        }else if(f.step>=TRAVEL_TICKS){
            f.ended=true;
            completeTravel(e,f);
        }
        return true;
    }
    private static Vec3 trajectory(Flight f,double t){
        return f.origin.add(f.dir.scale(f.distance*t)).add(0,4*f.height*t*(1-t),0);
    }
    /**
     * We cleared the kick delta after the previous move. A local player with velocity here got a
     * real external impulse; mobs may write AI intent before travel, so they use displacement only.
     */
    private static boolean externalImpulse(LivingEntity e,Flight f){
        if(e.position().distanceToSqr(f.lastTravelPosition)>EXTERNAL_DISPLACEMENT_SQR)return true;
        return e instanceof Player&&e.getDeltaMovement().lengthSqr()>EXTERNAL_VELOCITY_SQR;
    }
    /** Natural movement end keeps the cosmetic kick tail; interruption also cancels the pose. */
    private static void completeTravel(LivingEntity e,Flight f){
        if(FLIGHTS.get(e)!=f)return;
        if(ownsTravel(e))e.setDeltaMovement(Vec3.ZERO);
        // The server keeps a finished mob flight through this post-tick so the final swept hit is
        // still processed; the removal below then provides the eventual hit-state cleanup.
        if(e.level().isClientSide())FLIGHTS.remove(e);
    }
    /**
     * Single interruption boundary: remove the owned flight, cancel the matching kick pose and
     * sync a stop to the client that owns this motion. Genuine impulses are preserved.
     */
    private static void interruptFlight(LivingEntity e,Flight f,boolean preserveImpulse){
        interruptFlight(e,f,preserveImpulse,false);
    }
    private static void interruptFlight(LivingEntity e,Flight f,boolean preserveImpulse,boolean keepForFinalSweep){
        if(FLIGHTS.get(e)!=f)return;
        boolean deferCleanup=keepForFinalSweep&&!e.level().isClientSide()&&!(e instanceof ServerPlayer);
        if(!deferCleanup)FLIGHTS.remove(e);
        if(!preserveImpulse&&ownsTravel(e))e.setDeltaMovement(Vec3.ZERO);
        if(e.level().isClientSide()){
            if(e instanceof Player p&&p.isLocalPlayer())
                PacketDistributor.sendToServer(new PenguinKickCancel(f.sequence));
            return;
        }
        PenguinPoseSync.cancelKick(e,f.sequence);
        if(e instanceof ServerPlayer p)PacketDistributor.sendToPlayer(p,stopMotion(e,f.sequence));
    }
    /** Server players are client-authoritative; only the local mirror or a server mob moves here. */
    private static boolean ownsTravel(LivingEntity e){
        if(e.level().isClientSide())return e instanceof Player p&&p.isLocalPlayer();
        return !(e instanceof ServerPlayer);
    }
    private static PenguinKickMotion startMotion(LivingEntity e,Flight f){
        return new PenguinKickMotion(e.level().dimension().location(),f.sequence,true,
            e.getX(),e.getY(),e.getZ(),f.dir.x,f.dir.z,f.distance);
    }
    private static PenguinKickMotion stopMotion(LivingEntity e,long sequence){
        return new PenguinKickMotion(e.level().dimension().location(),sequence,false,0,0,0,0,0,0);
    }
    private static boolean valid(LivingEntity e,Flight f){
        return e.isAlive()&&!e.isRemoved()&&e.hasEffect(ModEffects.PENGUIN)&&!e.isPassenger()
            &&!e.isSwimming()&&!e.isFallFlying()&&e.tickCount-f.started<=TRAVEL_TICKS+5
            &&e.position().distanceToSqr(f.origin)<=f.originBoundSqr;
    }
    public static boolean mayHit(Player p,LivingEntity target){
        if(target==p||!target.isAlive()||!target.isAttackable()||target.isSpectator()
            ||p.isAlliedTo(target)||target.isAlliedTo(p)||!p.hasLineOfSight(target))return false;
        if(target instanceof Player other&&(!p.canHarmPlayer(other)
            ||!((ServerLevel)p.level()).getServer().isPvpAllowed()))return false;
        return true;
    }
    
    public static boolean independentMob(LivingEntity e){
        if(!(e instanceof Mob))return false;
        if(e.getType()==EntityType.IRON_GOLEM||e.getType()==EntityType.WARDEN
            ||!PenguinEntitySupport.isSupportedType(e.getType())
            ||e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==null)return false;
        // Hostile enemies keep their old eligibility; an optional maid joins only while the exact
        // melee task gate passes. Other friendly mobs remain excluded.
        return e instanceof net.minecraft.world.entity.monster.Enemy||MaidKickCompat.isEligibleMaid(e);
    }
    public static boolean blockOrdinaryMelee(Mob m){return independentMob(m)&&active(m)&&MOB_STRIKE.get()!=m;}
    @SubscribeEvent public static void choose(EntityTickEvent.Pre event){
        if(!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide()||!independentMob(mob)
            ||active(mob)||!mob.hasEffect(ModEffects.PENGUIN)||mob.tickCount%10!=Math.floorMod(mob.getId(),10))return;
        LivingEntity target=mob.getTarget();
        if(target==null||!target.isAlive()||mob.isAlliedTo(target)||!mob.hasLineOfSight(target))return;
        double d=mob.distanceToSqr(target);
        if(d>=4&&d<=20.25&&mob.getRandom().nextInt(3)==0)tryStart(mob);
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity e))return;
        Flight f=FLIGHTS.get(e);if(f==null)return;
        if(e.level().isClientSide()){
            if(e instanceof Player p&&p.isLocalPlayer()&&!valid(e,f))interruptFlight(e,f,false);
            return;
        }
        if(!valid(e,f)){interruptFlight(e,f,false);return;}
        // A maid's optional gate can change mid-leap: task switch, sit, ally/owner target or a
        // different current target must stop through the one existing interruption boundary.
        if(e instanceof Mob maid&&PenguinEntitySupport.isMaid(maid.getType())
            &&(!MaidKickCompat.isEligibleMaid(maid)||maid.getTarget()!=f.target)){
            interruptFlight(e,f,false);return;
        }
        
        if(e instanceof Mob mob){
            LivingEntity target=f.target;
            if(independentMob(mob)&&target!=null&&target==mob.getTarget()&&target.isAlive()
                &&!f.hit.contains(target.getUUID())&&!mob.isAlliedTo(target)&&mob.hasLineOfSight(target)
                &&mob.getBoundingBox().expandTowards(f.previous.subtract(mob.position())).inflate(.35,.15,.35).intersects(target.getBoundingBox())){
                MOB_STRIKE.set(mob);
                boolean hit;
                try{hit=mob.doHurtTarget(target);}finally{MOB_STRIKE.remove();}
                // A target in its invulnerability window stays unmarked and can be retried on a
                // later active tick; a successful hurt is recorded at most once per kick.
                if(hit)f.hit.add(target.getUUID());
            }
            f.previous=e.position();if(f.ended)FLIGHTS.remove(e);
        }
        if(e instanceof ServerPlayer p){
            Vec3 current=e.position();Vec3 segment=current.subtract(f.previous);
            var box=e.getBoundingBox().move(f.previous.subtract(current)).expandTowards(segment).inflate(.65,.25,.65);
            for(LivingEntity target:e.level().getEntitiesOfClass(LivingEntity.class,box)){
                if(f.hit.contains(target.getUUID())||!mayHit(p,target))continue;
                Vec3 offset=target.position().subtract(f.previous);
                if(offset.dot(f.dir)<-.15)continue;
                // Swept box broad phase followed by a capsule check avoids diagonal-corner hits.
                double len=segment.horizontalDistanceSqr();
                double t=len<1e-8?0:Math.max(0,Math.min(1,(offset.x*segment.x+offset.z*segment.z)/len));
                Vec3 closest=f.previous.add(segment.scale(t));
                double reach=.65+(e.getBbWidth()+target.getBbWidth())*.5;
                if(target.position().subtract(closest).horizontalDistanceSqr()>reach*reach)continue;
                if(attack(p,target,f.dir))f.hit.add(target.getUUID());
            }
            f.previous=current;
            if(e.tickCount-f.started>=TRAVEL_TICKS+2)FLIGHTS.remove(e);
        }
    }
    /** Returns the real PlayerAttackMixin hurt result so callers only record confirmed hits. */
    private static boolean attack(Player p,LivingEntity target,Vec3 dir){
        boolean sprint=p.isSprinting();float fall=p.fallDistance;
        boolean hit;
        ATTACKER.set(p);HIT.set(false);
        try{
            p.setSprinting(false);p.fallDistance=0;p.attack(target);
            hit=HIT.get();
            // Vanilla adds 0.5 strength per knockback level. Apply level II once, after native hurt.
            ATTACKER.remove();if(hit){
                var settings=PenguinKickSettings.get(((ServerLevel)p.level()).getServer()).values(p.getUUID());
                if(settings.knockbackLevel()>0)target.knockback(settings.knockbackStrength(),-dir.x,-dir.z);
            }
        }finally{ATTACKER.remove();HIT.remove();p.setSprinting(sprint);p.fallDistance=fall;}
        return hit;
    }
    @SubscribeEvent public static void sweep(SweepAttackEvent e){if(dispatching(e.getEntity()))e.setSweeping(false);}
    @SubscribeEvent public static void critical(CriticalHitEvent e){if(dispatching(e.getEntity())){e.setCriticalHit(false);e.setDamageMultiplier(1);}}
    @SubscribeEvent public static void knockback(LivingKnockBackEvent e){
        Player attacker=ATTACKER.get();
        LivingEntity target=e.getEntity();
        // Keep suppressing the vanilla extra knockback applied to the victim of our custom kick.
        if(attacker!=null&&target!=attacker){e.setStrength(0);return;}
        // A positive knockback actually received by an active kicker ends the kick, but its
        // impulse is left intact. Zero-strength events are not an interruption.
        if(e.getStrength()>0){
            Flight f=FLIGHTS.get(target);
            if(f!=null)interruptFlight(target,f,true);
        }
    }
}
