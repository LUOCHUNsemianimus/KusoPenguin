package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.MaledictusCompatClient;
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
 * Optional Cataclysm 1.21.1-3.33 ghost-layer bridge.
 *
 * <p>The pinned {@code Maledictus_Layer} computes its ghost render type, health alpha and hurt/
 * death color, then calls the concrete model's five-argument {@code renderToBuffer}. That call is
 * redirected so a penguin-replaced boss replays only the retained native head/weapons/spirit-wing
 * geometry into the exact same ghost buffer and pose. The native body geometry and a second
 * penguin body are never drawn in this pass. If the visual-only gate fails, the original call is
 * replayed with the exact original arguments.
 *
 * <p>The descriptor below is checked against the pinned JAR; the synthetic bridge overload taking
 * {@code Entity} is intentionally not targeted. The concrete entity parameter is coerced to
 * {@link LivingEntity} so no Cataclysm type is imported.
 */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.layer.Maledictus_Layer",
        remap = false)
public abstract class MaledictusLayerMixin {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/"
                    + "IABossMonsters/Maledictus/Maledictus_Entity;FFFFFF)V";
    private static final String NATIVE_RENDER =
            "Lcom/github/L_Ender/cataclysm/client/model/entity/Maledictus_Model;"
                    + "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    @Redirect(
            method = RENDER,
            at = @At(value = "INVOKE", target = NATIVE_RENDER),
            remap = false,
            require = 1
    )
    private void kusoPenguin$renderGhostRemainder(@Coerce EntityModel<?> model,
                                                  PoseStack poseStack, VertexConsumer buffer,
                                                  int packedLight, int packedOverlay, int color,
                                                  PoseStack originalPoseStack,
                                                  MultiBufferSource originalBuffer,
                                                  int originalLight,
                                                  @Coerce LivingEntity entity,
                                                  float limbSwing, float limbSwingAmount,
                                                  float partialTick, float ageInTicks,
                                                  float netHeadYaw, float headPitch) {
        if (!MaledictusCompatClient.renderGhostPass(entity, model, poseStack, buffer,
                packedLight, packedOverlay, color)) {
            model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
        }
    }
}
