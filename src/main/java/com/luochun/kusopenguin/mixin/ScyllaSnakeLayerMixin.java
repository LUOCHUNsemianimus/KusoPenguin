package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.ScyllaCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional Cataclysm 1.21.1-3.33 Scylla snake ghost layer.
 *
 * <p>The pinned layer chooses its ghost {@code RenderType}, fixed alpha and overlay, then submits
 * the whole five-argument model. This redirect keeps that exact buffer/color but replays only the
 * belt and shoulder snake terminal branches, so no native torso and no second penguin body are
 * drawn under the snake texture. A failed visual-only gate replays the original call.
 */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.layer.Scylla_Snake_Layer",
        remap = false)
public abstract class ScyllaSnakeLayerMixin {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/"
                    + "IABossMonsters/Scylla/Scylla_Entity;FFFFFF)V";
    private static final String NATIVE_RENDER =
            "Lcom/github/L_Ender/cataclysm/client/model/entity/Scylla_Model;"
                    + "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    @Redirect(
            method = RENDER,
            at = @At(value = "INVOKE", target = NATIVE_RENDER),
            remap = false,
            require = 1
    )
    private void kusoPenguin$renderScyllaSnake(@Coerce EntityModel<?> model, PoseStack poseStack,
                                               VertexConsumer buffer, int packedLight,
                                               int packedOverlay, int color,
                                               PoseStack originalPoseStack,
                                               MultiBufferSource originalBuffer,
                                               int originalLight,
                                               @Coerce LivingEntity entity,
                                               float limbSwing, float limbSwingAmount,
                                               float partialTicks, float ageInTicks,
                                               float netHeadYaw, float headPitch) {
        if (!ScyllaCompatClient.renderSnakePass(entity, model, poseStack, buffer,
                packedLight, packedOverlay, color)) {
            model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
        }
    }
}
