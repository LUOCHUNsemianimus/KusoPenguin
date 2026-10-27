package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.revival.RevivalCorpse;
import com.luochun.kusopenguin.revival.RevivalIds;
import com.luochun.kusopenguin.revival.RevivalManager;
import com.luochun.kusopenguin.revival.RevivalPackets;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private static final int FILM_PULSE_INTERVAL = 5;
    private static final int FILM_HOLD_LIMIT_TICKS = RevivalManager.FILM_DURATION_TICKS + 20;
    private static final KeyMapping REVIVAL_KEY = new KeyMapping(
            "key.kuso_penguin.revival", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.kuso_penguin");

    private static UUID filmToken;
    private static InteractionHand filmHand;
    private static int filmSlot;
    private static ItemStack filmSnapshot = ItemStack.EMPTY;
    private static ResourceLocation filmDimension;
    private static int filmPulse;
    private static int filmAge;
    private static boolean filmActive;

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

    /** Deterministic main-hand first, then offhand, using the shared lazy owned-film check. */
    public static InteractionHand findHeldFilm(Player player) {
        if (player == null) {
            return null;
        }
        if (com.luochun.kusopenguin.compat.MaidRevivalCompat.validOwnedFilm(
                player, player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (com.luochun.kusopenguin.compat.MaidRevivalCompat.validOwnedFilm(
                player, player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static boolean filmActive() {
        return filmActive;
    }

    /** Opens one hold token per key press; START is never retried while the key stays down. */
    public static boolean startFilmHold(Minecraft minecraft, InteractionHand hand) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || hand == null) {
            return false;
        }
        ItemStack stack = hand == InteractionHand.MAIN_HAND
                ? player.getMainHandItem() : player.getOffhandItem();
        if (!RevivalIds.isFilmItem(stack)) {
            return false;
        }
        filmToken = UUID.randomUUID();
        filmHand = hand;
        filmSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 0;
        filmSnapshot = stack.copy();
        filmDimension = minecraft.level.dimension().location();
        filmPulse = 0;
        filmAge = 0;
        filmActive = true;
        sendFilmControl(hand, RevivalPackets.Action.START);
        return true;
    }

    /**
     * Release/GUI/unfocus/disconnect/dimension/hand/slot/data/count/effect changes all stop the
     * local hold immediately. Pulses only refresh an existing server channel; they never open one.
     */
    public static void tickFilmHold(Minecraft minecraft) {
        if (!filmActive) {
            return;
        }
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !minecraft.isWindowActive() || filmToken == null || filmDimension == null
                || !filmDimension.equals(minecraft.level.dimension().location())
                || !player.isAlive() || !player.hasEffect(ModEffects.PENGUIN)
                || !matchesHeld(player)) {
            stopFilmHold();
            return;
        }
        if (++filmAge > FILM_HOLD_LIMIT_TICKS) {
            stopFilmHold();
            return;
        }
        if (++filmPulse >= FILM_PULSE_INTERVAL) {
            filmPulse = 0;
            sendFilmControl(filmHand, RevivalPackets.Action.PULSE);
        }
    }

    private static boolean matchesHeld(Player player) {
        ItemStack live;
        if (filmHand == InteractionHand.MAIN_HAND) {
            if (player.getInventory().selected != filmSlot) {
                return false;
            }
            live = player.getMainHandItem();
        } else {
            live = player.getOffhandItem();
        }
        return !live.isEmpty() && ItemStack.matches(live, filmSnapshot);
    }

    /** Sends the explicit STOP before clearing local state; safe before the START ack arrives. */
    public static void stopFilmHold() {
        if (filmActive && filmToken != null) {
            sendFilmControl(filmHand, RevivalPackets.Action.STOP);
        }
        clearFilmHold();
    }

    /** Disconnect/clone path: never send on a dead connection. */
    public static void resetFilmHold() {
        clearFilmHold();
    }

    private static void clearFilmHold() {
        filmToken = null;
        filmHand = null;
        filmSlot = 0;
        filmSnapshot = ItemStack.EMPTY;
        filmDimension = null;
        filmPulse = 0;
        filmAge = 0;
        filmActive = false;
    }

    /**
     * An explicit inactive state for our token ends pulsing immediately. A state for another
     * token (or the corpse token) is ignored, so a stale stop cannot affect a newer hold.
     */
    static void handleState(RevivalPackets.State payload) {
        if (!payload.maidTarget() || payload.active() || filmToken == null
                || !filmToken.equals(payload.target())) {
            return;
        }
        clearFilmHold();
    }

    private static void sendFilmControl(InteractionHand hand, RevivalPackets.Action action) {
        if (filmToken == null || hand == null) {
            return;
        }
        PacketDistributor.sendToServer(new RevivalPackets.Control(filmToken, hand, action));
    }

    public static void sendRequest(UUID target) {
        PacketDistributor.sendToServer(new RevivalPackets.Request(target));
    }

    /**
     * Crosshair-first target for the single K press on a dead player. A dead player is identified
     * by the persistent corpse marker's own entity UUID, never by the owner UUID. Maid graves are
     * not targets anymore; their native right-click returns the film.
     */
    public static UUID findTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Player self = minecraft.player;
        if (self == null || minecraft.level == null) {
            return null;
        }
        UUID active = RevivalClientState.corpseTarget(self);
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
