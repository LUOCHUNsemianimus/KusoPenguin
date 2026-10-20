package com.luochun.kusopenguin.client;
import com.luochun.kusopenguin.animation.*;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
public final class PenguinKickInput {
    private static LocalPlayer cooldownOwner;
    private static long readyAt;
    private PenguinKickInput(){}
    public static boolean attack(){
        var p=Minecraft.getInstance().player;
        if(p==null||!p.isAlive()||!p.hasEffect(ModEffects.PENGUIN)||!p.isSprinting()||p.isSpectator()
            ||!p.onGround()||p.isCrouching()||p.isPassenger()||p.isSwimming()||p.isFallFlying()
            ||p==cooldownOwner&&p.level().getGameTime()<readyAt)return false;
        PacketDistributor.sendToServer(PenguinKickRequest.INSTANCE);return true;
    }
    /** A confirmed server action, not a click or ordinary attack, starts this cooldown. */
    public static void receive(PenguinPosePayload packet){
        var p=Minecraft.getInstance().player;
        if(p!=null&&packet.playing()&&packet.action()==PenguinAction.FLYING_KICK
            &&p.getId()==packet.entityId()&&p.getUUID().equals(packet.uuid())
            &&p.level().dimension().location().equals(packet.dimension())){
            cooldownOwner=p;readyAt=packet.startedGameTime()+PenguinPoseSync.PLAYER_KICK_COOLDOWN;
        }
    }
    public static boolean moving(){var p=Minecraft.getInstance().player;return p!=null&&PenguinKick.active(p);}
    public static void receive(PenguinKickMotion motion){
        var p=Minecraft.getInstance().player;
        if(p!=null&&p.level().dimension().location().equals(motion.dimension()))
            PenguinKick.startLocal(p,new Vec3(motion.x(),motion.y(),motion.z()),new Vec3(motion.dx(),0,motion.dz()));
    }
}
