package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.registry.ModEffects;
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
    private static final Map<LivingEntity,Flight> FLIGHTS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Mob> MOB_STRIKE=new ThreadLocal<>();
    private static final ThreadLocal<Player> ATTACKER=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HIT=ThreadLocal.withInitial(()->false);
    private static final class Flight {
        final Vec3 origin,dir; final double distance,height; final int started;
        final double originBoundSqr,maxStepSqr;
        int step; boolean ended; final LivingEntity target; Vec3 previous; final Set<UUID> hit=new HashSet<>();
        Flight(LivingEntity e,Vec3 origin,Vec3 dir,double distance,double height){
            this.origin=origin;this.previous=e.position();this.dir=dir;this.distance=distance;this.height=height;this.started=e.tickCount;this.target=e instanceof Mob mob?mob.getTarget():null;
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
        // Maid is eligible for the shared body/taunt/dance/death pose, but must never gain the
        // hostile auto-kick behaviour or be launched at her owner/target by this path.
        if(PenguinEntitySupport.isMaid(e.getType()))return false;
        if(e instanceof Mob mob && com.luochun.kusopenguin.music.DanceManager.pausesAI(mob))return false;
        if(!(e.level() instanceof ServerLevel)||!e.isAlive()||!e.hasEffect(ModEffects.PENGUIN)
            ||!PenguinEntitySupport.isSupportedType(e.getType())||!e.onGround()||e.isPassenger()
            ||e.isSwimming()||e.isFallFlying()||e.isSpectator()||e.isBaby()||active(e))return false;
        if(e instanceof Player p&&(!p.isSprinting()||p.isCrouching()))return false;
        if(!PenguinPoseSync.start(e,PenguinAction.FLYING_KICK))return false;
        Vec3 look=e instanceof Mob m&&m.getTarget()!=null?m.getTarget().position().subtract(e.position()):e.getLookAngle();Vec3 dir=new Vec3(look.x,0,look.z).normalize();
        double baseDistance=e instanceof Player?4:independentMob(e)?3:1.5,height=e instanceof Player?1:.75;
        double distance=baseDistance*speedScale(e);
        FLIGHTS.put(e,new Flight(e,e.position(),dir,distance,height));
        e.fallDistance=0;
        if(e instanceof ServerPlayer p)PacketDistributor.sendToPlayer(p,new PenguinKickMotion(
            e.level().dimension().location(),e.getX(),e.getY(),e.getZ(),dir.x,dir.z,distance));
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
    public static void startLocal(Player p,Vec3 origin,Vec3 direction,double distance){
        if(p.level().isClientSide())FLIGHTS.put(p,new Flight(p,origin,direction,distance,1));
    }
    /** Called instead of ordinary travel only for the local player or a server-controlled mob. */
    public static boolean travel(LivingEntity e){
        Flight f=FLIGHTS.get(e);
        if(f==null||e instanceof ServerPlayer||e.level().isClientSide()&&!(e instanceof Player p&&p.isLocalPlayer()))return false;
        if(!valid(e,f)||f.step>=TRAVEL_TICKS){FLIGHTS.remove(e);return false;}
        double t=(++f.step)/(double)TRAVEL_TICKS;
        Vec3 next=f.origin.add(f.dir.scale(f.distance*t)).add(0,4*f.height*t*(1-t),0);
        Vec3 delta=next.subtract(e.position());
        // A server-approved start never permits catch-up teleports after external displacement.
        if(delta.lengthSqr()>f.maxStepSqr){FLIGHTS.remove(e);return false;}
        e.setDeltaMovement(delta);e.move(MoverType.SELF,delta);e.fallDistance=0;
        if(e.horizontalCollision||e.verticalCollision&&t<.5||f.step>=TRAVEL_TICKS){
            f.ended=true;if(e.level().isClientSide())FLIGHTS.remove(e);e.setDeltaMovement(Vec3.ZERO);
        }
        return true;
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
        return e instanceof Mob&&e instanceof net.minecraft.world.entity.monster.Enemy
            &&e.getType()!=EntityType.IRON_GOLEM&&e.getType()!=EntityType.WARDEN
            &&PenguinEntitySupport.isSupportedType(e.getType())
            &&e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)!=null;
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
        if(!valid(e,f)){FLIGHTS.remove(e);return;}
        
        if(!e.level().isClientSide()&&e instanceof Mob mob){
            LivingEntity target=f.target;
            if(independentMob(mob)&&target!=null&&target==mob.getTarget()&&target.isAlive()
                &&!f.hit.contains(target.getUUID())&&!mob.isAlliedTo(target)&&mob.hasLineOfSight(target)
                &&mob.getBoundingBox().expandTowards(f.previous.subtract(mob.position())).inflate(.35,.15,.35).intersects(target.getBoundingBox())){
                f.hit.add(target.getUUID());MOB_STRIKE.set(mob);
                try{mob.doHurtTarget(target);}finally{MOB_STRIKE.remove();}
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
                f.hit.add(target.getUUID());attack(p,target,f.dir);
            }
            f.previous=current;
            if(e.tickCount-f.started>=TRAVEL_TICKS+2)FLIGHTS.remove(e);
        }
    }
    private static void attack(Player p,LivingEntity target,Vec3 dir){
        boolean sprint=p.isSprinting();float fall=p.fallDistance;
        ATTACKER.set(p);HIT.set(false);
        try{
            p.setSprinting(false);p.fallDistance=0;p.attack(target);
            boolean hit=HIT.get();
            // Vanilla adds 0.5 strength per knockback level. Apply level II once, after native hurt.
            ATTACKER.remove();if(hit){
                var settings=PenguinKickSettings.get(((ServerLevel)p.level()).getServer()).values(p.getUUID());
                if(settings.knockbackLevel()>0)target.knockback(settings.knockbackStrength(),-dir.x,-dir.z);
            }
        }finally{ATTACKER.remove();HIT.remove();p.setSprinting(sprint);p.fallDistance=fall;}
    }
    @SubscribeEvent public static void sweep(SweepAttackEvent e){if(dispatching(e.getEntity()))e.setSweeping(false);}
    @SubscribeEvent public static void critical(CriticalHitEvent e){if(dispatching(e.getEntity())){e.setCriticalHit(false);e.setDamageMultiplier(1);}}
    @SubscribeEvent public static void knockback(LivingKnockBackEvent e){if(ATTACKER.get()!=null)e.setStrength(0);}
}
