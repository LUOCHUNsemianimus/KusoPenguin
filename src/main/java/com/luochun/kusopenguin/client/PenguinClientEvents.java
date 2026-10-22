package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
        PenguinRenderContext.setModel(penguin);

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
        addLayer(event.getRenderer(EntityType.CREEPER), penguin);
        addLayer(event.getRenderer(EntityType.ENDERMAN), penguin);
        addLayer(event.getRenderer(EntityType.WITHER_SKELETON), penguin);
        addLayer(event.getRenderer(EntityType.ZOMBIE_VILLAGER), penguin);
        addLayer(event.getRenderer(EntityType.IRON_GOLEM), penguin);
        addLayer(event.getRenderer(EntityType.WARDEN), penguin);

        // Prone adults: same shared body layer, static horizontal pose. Cave spider keeps its
        // native renderer .7 overall scale because only the inner body transform is ours.
        addLayer(event.getRenderer(EntityType.COW), penguin);
        addLayer(event.getRenderer(EntityType.SHEEP), penguin);
        addLayer(event.getRenderer(EntityType.PIG), penguin);
        addLayer(event.getRenderer(EntityType.CAT), penguin);
        addLayer(event.getRenderer(EntityType.WOLF), penguin);
        addLayer(event.getRenderer(EntityType.SPIDER), penguin);
        addLayer(event.getRenderer(EntityType.CAVE_SPIDER), penguin);

        // Optional maid renderer. Use the optional registry lookup: the defaulted get() returns
        // PIG for a missing id and would attach a duplicate penguin layer to the standalone pig.
        // No maid class is referenced from this client event.
        BuiltInRegistries.ENTITY_TYPE.getOptional(
                        ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "maid"))
                .ifPresent(maid -> addLayer(event.getRenderer(maid), penguin));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLayer(EntityRenderer<?> renderer, PenguinBodyModel penguin) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(new PenguinBodyLayer(livingRenderer, penguin));
        }
    }
}
