package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.revival.RevivalCorpse;
import com.luochun.kusopenguin.revival.RevivalIds;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class RevivalClient {
    private static final double RANGE = 4.0D;
    private static final double LOOK_DOT = 0.82D;
    private static final KeyMapping REVIVAL_KEY = new KeyMapping(
            "key.kuso_penguin.revival", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.kuso_penguin");

    private RevivalClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(REVIVAL_KEY);
        RevivalClientState.install();
    }

    public static KeyMapping revivalKey() {
        return REVIVAL_KEY;
    }

    public static void sendRequest(UUID target) {
        PacketDistributor.sendToServer(new com.luochun.kusopenguin.revival.RevivalPackets.Request(target));
    }

    /**
     * Crosshair-first target for the single K press. A dead player is identified by the persistent
     * corpse marker's own entity UUID (the death-generation token), never by the owner UUID.
     */
    public static UUID findTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Player self = minecraft.player;
        if (self == null || minecraft.level == null) {
            return null;
        }
        UUID active = RevivalClientState.target(self);
        if (active != null) {
            return active;
        }
        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getLookAngle();
        UUID best = null;
        double bestDot = LOOK_DOT;
        for (RevivalCorpse corpse : minecraft.level.getEntitiesOfClass(RevivalCorpse.class,
                self.getBoundingBox().inflate(RANGE))) {
            if (corpse.isRemoved() || corpse.ownerUuid() == null
                    || corpse.ownerUuid().equals(self.getUUID())
                    || !self.hasLineOfSight(corpse)) {
                continue;
            }
            double dot = lookDot(eye, look, corpse.getBoundingBox().getCenter());
            if (dot > bestDot) {
                bestDot = dot;
                best = corpse.getUUID();
            }
        }
        for (Entity entity : minecraft.level.getEntitiesOfClass(Entity.class,
                self.getBoundingBox().inflate(RANGE), RevivalIds::isTombstone)) {
            if (!self.hasLineOfSight(entity)) {
                continue;
            }
            double dot = lookDot(eye, look, entity.getBoundingBox().getCenter());
            if (dot > bestDot) {
                bestDot = dot;
                best = entity.getUUID();
            }
        }
        return best;
    }

    private static double lookDot(Vec3 eye, Vec3 look, Vec3 target) {
        Vec3 delta = target.subtract(eye);
        double distance = delta.length();
        if (distance <= 0.0D || distance > RANGE) {
            return -1.0D;
        }
        return delta.normalize().dot(look);
    }
}
