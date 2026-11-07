package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.PenguinDeathClient;
import com.luochun.kusopenguin.client.dance.DanceClient;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT)
public final class RevivalClientEvents {
    private static int tick;
    private static ResourceLocation lastDimension;
    private static boolean lastPhysicalDown;

    private RevivalClientEvents() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        RevivalClientState.cleanup();
        if (minecraft.player == null || minecraft.level == null) {
            RevivalCorpseRenderer.clear();
            RevivalClient.resetFilmHold();
            lastDimension = null;
            lastPhysicalDown = false;
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            RevivalClientState.clear();
            RevivalClient.stopFilmHold();
        }
        boolean physical = RevivalClient.revivalKey().isDown();
        boolean eligible = physical && minecraft.screen == null && minecraft.isWindowActive();
        // Edge detection uses the physical key so closing a GUI while K is still held cannot
        // start a second ritual without a fresh press.
        if (physical && !lastPhysicalDown && eligible) {
            pressOrHold(minecraft);
        }
        if (eligible) {
            RevivalClient.tickFilmHold(minecraft);
        } else {
            RevivalClient.stopFilmHold();
        }
        lastPhysicalDown = physical;
        if (++tick % 10 == 0) {
            RevivalCorpseRenderer.updateHiddenOwners(minecraft.level);
            prompt(minecraft);
        }
    }

    private static void pressOrHold(Minecraft minecraft) {
        InteractionHand film = RevivalClient.findHeldFilm(minecraft.player);
        if (film != null) {
            RevivalClient.startFilmHold(minecraft, film);
            return;
        }
        LivingEntity boss = danceBoss(minecraft);
        if (boss != null) {
            DanceClient.openWheel(boss.getUUID(), boss.getDisplayName());
            return;
        }
        LivingEntity fake = fakeDanceTarget(minecraft);
        if (fake != null) {
            DanceClient.openWheel(fake.getUUID(), fake.getDisplayName());
            return;
        }
        UUID target = RevivalClient.findTarget();
        if (target != null) {
            RevivalClient.sendRequest(target);
            return;
        }
        // Film, a valid exact boss and a valid corpse keep their original precedence. With the
        // penguin effect the same press falls through to the quick dance wheel; without it K must
        // not start a dance.
        if (minecraft.player.hasEffect(ModEffects.PENGUIN)) {
            DanceClient.openWheel();
        }
    }

    /**
     * Exact crosshair route for the three dancing bosses. The actual EntityHitResult supplies the
     * ray target; range is measured to the native bounding box so a large boss is not rejected by
     * its centre distance.
     */
    private static LivingEntity danceBoss(Minecraft minecraft) {
        if (minecraft.player == null || !minecraft.player.hasEffect(ModEffects.PENGUIN)
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity boss)
                || boss.isRemoved() || !boss.isAlive()
                || !boss.hasEffect(ModEffects.PENGUIN)
                || !PenguinEntitySupport.isDanceBoss(boss.getType())) {
            return null;
        }
        if (boss.getBoundingBox().distanceToSqr(minecraft.player.getEyePosition()) > 16.0D) {
            return null;
        }
        return boss;
    }

    /**
     * Exact crosshair route for a server-marked SiliconeDolls fake player. The marker is the only
     * fake identity; the client still applies the same effect/alive/range gates as the boss route.
     */
    private static LivingEntity fakeDanceTarget(Minecraft minecraft) {
        if (minecraft.player == null || !minecraft.player.hasEffect(ModEffects.PENGUIN)
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity fake)
                || fake.isRemoved() || !fake.isAlive()
                || !fake.hasEffect(ModEffects.PENGUIN)
                || !DanceClient.isTrackedFakeTarget(fake.getUUID())) {
            return null;
        }
        if (fake.getBoundingBox().distanceToSqr(minecraft.player.getEyePosition()) > 16.0D) {
            return null;
        }
        return fake;
    }

    private static void prompt(Minecraft minecraft) {
        if (RevivalClientState.pole(minecraft.player)
                || !minecraft.player.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        InteractionHand film = RevivalClient.findHeldFilm(minecraft.player);
        if (film != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.kuso_penguin.revival.film_prompt",
                    RevivalClient.revivalKey().getTranslatedKeyMessage()), true);
            return;
        }
        if (RevivalClient.findTarget() != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.kuso_penguin.revival.prompt",
                    RevivalClient.revivalKey().getTranslatedKeyMessage()), true);
        }
    }

    @SubscribeEvent
    public static void hideDeadPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().isDeadOrDying()
                && !PenguinDeathClient.rendering(event.getEntity())
                && !RevivalCorpseRenderer.isRenderingVisual(event.getEntity())
                && RevivalCorpseRenderer.hides(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RevivalClient.resetFilmHold();
        RevivalClientState.clear();
        RevivalCorpseRenderer.clear();
        lastDimension = null;
        lastPhysicalDown = false;
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        RevivalClient.resetFilmHold();
        RevivalClientState.clear();
        RevivalCorpseRenderer.clear();
        lastDimension = null;
        lastPhysicalDown = false;
    }
}
