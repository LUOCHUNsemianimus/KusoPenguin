package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PenguinClientEvents {
    private PenguinClientEvents() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PenguinBodyModel.LAYER, PenguinBodyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        PenguinBodyModel penguin = new PenguinBodyModel(
                event.getEntityModels().bakeLayer(PenguinBodyModel.LAYER));

        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new PenguinBodyLayer<>(renderer, penguin));
            }
        }

        addLayer(event.getRenderer(EntityType.SKELETON), penguin);
        addLayer(event.getRenderer(EntityType.ZOMBIE), penguin);
        addLayer(event.getRenderer(EntityType.VILLAGER), penguin);
        addLayer(event.getRenderer(EntityType.PIGLIN), penguin);
        addLayer(event.getRenderer(EntityType.ZOMBIFIED_PIGLIN), penguin);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayer(EntityRenderer<?> renderer, PenguinBodyModel penguin) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(new PenguinBodyLayer(livingRenderer, penguin));
        }
    }
}
