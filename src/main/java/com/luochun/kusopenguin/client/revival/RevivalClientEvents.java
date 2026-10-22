package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.client.PenguinDeathClient;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private RevivalClientEvents() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        RevivalClientState.cleanup();
        if (minecraft.player == null || minecraft.level == null) {
            RevivalCorpseRenderer.clear();
            lastDimension = null;
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            RevivalClientState.clear();
        }
        while (RevivalClient.revivalKey().consumeClick()) {
            pressKey(minecraft);
        }
        if (++tick % 10 == 0) {
            RevivalCorpseRenderer.updateHiddenOwners(minecraft.level);
            prompt(minecraft);
        }
    }

    private static void pressKey(Minecraft minecraft) {
        UUID target = RevivalClient.findTarget();
        if (target != null) {
            RevivalClient.sendRequest(target);
        } else {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.kuso_penguin.revival.no_target"), true);
        }
    }

    private static void prompt(Minecraft minecraft) {
        if (RevivalClientState.target(minecraft.player) != null
                || !minecraft.player.hasEffect(ModEffects.PENGUIN)) {
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
        RevivalClientState.clear();
        RevivalCorpseRenderer.clear();
        lastDimension = null;
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        RevivalClientState.clear();
        RevivalCorpseRenderer.clear();
        lastDimension = null;
    }
}
