package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Detached render-only copies: never inserted into a level, ticked, hit, or used for loot. */
@EventBusSubscriber(modid=KusoPenguin.MODID,bus=EventBusSubscriber.Bus.GAME,value=Dist.CLIENT)
public final class PenguinDeathClient {
    private record Ghost(LivingEntity model,Vec3 position,float yaw,long started,Vec3[] path){
        Vec3 positionAt(float ticks){
            float frame=Math.max(0,Math.min(PenguinKick.TRAVEL_TICKS,ticks));
            int i=(int)frame;
            return i==PenguinKick.TRAVEL_TICKS?path[i]:path[i].lerp(path[i+1],frame-i);
        }
    }
    private static final List<Ghost> GHOSTS=new ArrayList<>();
    private static final Map<LivingEntity,Long> HIDDEN=new IdentityHashMap<>();
    private static Ghost current;
    private PenguinDeathClient(){}
    public static boolean rendering(LivingEntity e){return current!=null&&current.model()==e;}
    public static float age(LivingEntity e,float partial){
        return rendering(e)?Math.max(0,(e.level().getGameTime()-current.started()+partial)/20F):-1;
    }
    public static void receive(PenguinPosePayload packet){
        var mc=Minecraft.getInstance();
        if(!packet.playing()||packet.action()!=PenguinAction.DEATH||mc.level==null
            ||!mc.level.dimension().location().equals(packet.dimension()))return;
        var source=mc.level.getEntity(packet.entityId());
        if(!(source instanceof LivingEntity e)||!e.getUUID().equals(packet.uuid())||HIDDEN.containsKey(e)
            ||!PenguinEntitySupport.isSupportedType(e.getType())||e.isBaby())return;
        LivingEntity copy;
        if(e instanceof AbstractClientPlayer player)copy=new RemotePlayer(mc.level,player.getGameProfile());
        else {
            Entity created=e.getType().create(mc.level);
            if(!(created instanceof LivingEntity living))return;
            copy=living;
        }
        copy.load(e.saveWithoutId(new CompoundTag()));
        for(EquipmentSlot slot:EquipmentSlot.values())copy.setItemSlot(slot,e.getItemBySlot(slot).copy());
        copy.setPos(e.position());copy.setYRot(e.getYRot());copy.setXRot(e.getXRot());
        copy.yBodyRot=e.yBodyRot;copy.yBodyRotO=e.yBodyRot;copy.yHeadRot=e.yHeadRot;copy.yHeadRotO=e.yHeadRot;
        copy.tickCount=e.tickCount;copy.deathTime=0;copy.hurtTime=0;copy.setPose(Pose.STANDING);
        if(GHOSTS.size()>=128)GHOSTS.remove(0);
        GHOSTS.add(new Ghost(copy,e.position(),e.getYRot(),packet.startedGameTime(),deathPath(copy)));
        HIDDEN.put(e,mc.level.getGameTime());
    }
    /** Pure geometry queries: no entity movement, world registration, ticks or game events. */
    private static Vec3[] deathPath(LivingEntity copy){
        Vec3 origin=copy.position();
        Vec3 back=Vec3.directionFromRotation(0,copy.yBodyRot).scale(-1);
        double distance=copy instanceof AbstractClientPlayer?1:3;
        double height=copy instanceof AbstractClientPlayer?.35:.75;
        Vec3[] path=new Vec3[PenguinKick.TRAVEL_TICKS+1];path[0]=origin;
        var box=copy.getBoundingBox();
        for(int i=1;i<path.length;i++){
            double t=i/(double)PenguinKick.TRAVEL_TICKS;
            Vec3 desired=origin.add(back.scale(distance*t)).add(0,4*height*t*(1-t),0);
            Vec3 delta=Entity.collideBoundingBox(copy,desired.subtract(path[i-1]),box,copy.level(),List.of());
            path[i]=path[i-1].add(delta);box=box.move(delta);
        }
        return path;
    }
    @SubscribeEvent public static void hide(RenderLivingEvent.Pre<?,?> event){
        if(HIDDEN.containsKey(event.getEntity())&&!rendering(event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void hidePlayer(RenderPlayerEvent.Pre event){
        if(HIDDEN.containsKey(event.getEntity())&&!rendering(event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(level==null){GHOSTS.clear();HIDDEN.clear();return;}
        GHOSTS.removeIf(g->g.model().level()!=level||(level.getGameTime()-g.started())/20F>=PenguinAction.DEATH.durationSeconds());
        HIDDEN.entrySet().removeIf(entry->entry.getKey().level()!=level||(entry.getKey().isAlive()||entry.getKey().isRemoved())&&level.getGameTime()-entry.getValue()>45);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){GHOSTS.clear();HIDDEN.clear();current=null;}
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||GHOSTS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        PoseStack stack=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().getPosition();
        for(Ghost ghost:GHOSTS){
            if(ghost.model().level()!=mc.level||camera.distanceToSqr(ghost.position())>128*128
                ||(mc.level.getGameTime()-ghost.started()+partial)/20F>=PenguinAction.DEATH.durationSeconds())continue;
            stack.pushPose();current=ghost;
            try{
                Vec3 worldPos=ghost.positionAt(mc.level.getGameTime()-ghost.started()+partial);
                ghost.model().setPos(worldPos);
                Vec3 pos=worldPos.subtract(camera);stack.translate(pos.x,pos.y,pos.z);
                EntityRenderer renderer=mc.getEntityRenderDispatcher().getRenderer(ghost.model());
                renderer.render(ghost.model(),ghost.yaw(),partial,stack,buffers,renderer.getPackedLightCoords(ghost.model(),partial));
            }finally{current=null;stack.popPose();}
        }
    }
}
