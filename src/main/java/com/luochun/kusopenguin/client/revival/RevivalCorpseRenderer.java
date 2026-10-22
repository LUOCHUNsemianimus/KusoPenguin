package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.revival.RevivalCorpse;
import com.luochun.kusopenguin.revival.RevivalEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class RevivalCorpseRenderer extends EntityRenderer<RevivalCorpse> {
    private static final int PENGUIN_DEATH_VISUAL_TICKS = 42;
    private static final Set<UUID> HIDDEN_OWNERS = Collections.synchronizedSet(new HashSet<>());
    private static final WeakHashMap<RevivalCorpse, RemotePlayer> VISUALS = new WeakHashMap<>();
    private static Entity RENDERING_VISUAL;

    public RevivalCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RevivalEntities.CORPSE.get(), RevivalCorpseRenderer::new);
    }

    public static void updateHiddenOwners(ClientLevel level) {
        Set<UUID> owners = new HashSet<>();
        for (var entity : level.entitiesForRendering()) {
            if (entity instanceof RevivalCorpse corpse && corpse.ownerUuid() != null) {
                owners.add(corpse.ownerUuid());
            }
        }
        synchronized (HIDDEN_OWNERS) {
            HIDDEN_OWNERS.clear();
            HIDDEN_OWNERS.addAll(owners);
        }
    }

    public static boolean hides(UUID owner) {
        return HIDDEN_OWNERS.contains(owner);
    }

    /**
     * True only while our own render-only RemotePlayer visual is being drawn. The same-UUID owner
     * hide filter must not cancel that draw.
     */
    public static boolean isRenderingVisual(Entity entity) {
        return RENDERING_VISUAL == entity;
    }

    public static void clear() {
        HIDDEN_OWNERS.clear();
        VISUALS.clear();
    }

    @Override
    public void render(RevivalCorpse corpse, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        UUID owner = corpse.ownerUuid();
        if (owner == null) {
            return;
        }
        if (corpse.penguin()
                && corpse.level().getGameTime() - corpse.createdGameTime() < PENGUIN_DEATH_VISUAL_TICKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RemotePlayer visual = VISUALS.computeIfAbsent(corpse, ignored -> {
            PlayerInfo info = minecraft.getConnection() == null ? null
                    : minecraft.getConnection().getPlayerInfo(owner);
            GameProfile profile = info == null
                    ? new GameProfile(owner, corpse.ownerName())
                    : info.getProfile();
            return new RemotePlayer((ClientLevel) corpse.level(), profile);
        });
        visual.setPos(corpse.position());
        visual.setYRot(corpse.getYRot());
        visual.setXRot(0.0F);
        visual.yBodyRot = corpse.getYRot();
        visual.yBodyRotO = corpse.getYRot();
        visual.yHeadRot = corpse.getYRot();
        visual.yHeadRotO = corpse.getYRot();
        visual.tickCount = corpse.tickCount;
        visual.setPose(Pose.STANDING);
        poseStack.pushPose();
        try {
            // Recognisable lying-down pose; the RemotePlayer is never added to the level.
            poseStack.translate(0.0D, 0.08D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            @SuppressWarnings("unchecked")
            EntityRenderer<RemotePlayer> renderer = (EntityRenderer<RemotePlayer>)
                    minecraft.getEntityRenderDispatcher().getRenderer(visual);
            RENDERING_VISUAL = visual;
            renderer.render(visual, corpse.getYRot(), partialTick, poseStack, buffer, packedLight);
        } finally {
            RENDERING_VISUAL = null;
            poseStack.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RevivalCorpse entity) {
        UUID owner = entity.ownerUuid();
        return owner == null ? DefaultPlayerSkin.getDefaultTexture()
                : DefaultPlayerSkin.get(owner).texture();
    }
}
