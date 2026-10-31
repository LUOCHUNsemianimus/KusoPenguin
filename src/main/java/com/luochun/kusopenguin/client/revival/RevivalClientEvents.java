package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.client.PenguinDeathClient;
import com.luochun.kusopenguin.client.dance.DanceClient;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
        UUID target = RevivalClient.findTarget();
        if (target != null) {
            RevivalClient.sendRequest(target);
            return;
        }
        // Film and a valid corpse keep their original paths. With the penguin effect the same
        // press falls through to the quick dance wheel; without it K must not start a dance.
        if (minecraft.player.hasEffect(ModEffects.PENGUIN)) {
            DanceClient.openWheel();
        }
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
