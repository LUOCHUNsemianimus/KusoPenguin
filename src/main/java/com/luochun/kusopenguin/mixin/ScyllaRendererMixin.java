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
 * Optional Cataclysm 1.21.1-3.33 Scylla main-pass bridge.
 *
 * <p>Scylla overrides {@code LivingEntityRenderer.render} without calling super, so the generic
 * {@code LivingEntityRendererMixin} never sees its model submission. The pinned renderer calls the
 * concrete five-argument {@code Scylla_Model.renderToBuffer} directly; that call is redirected here.
 * The native renderer still owns pose setup, death alpha, layers, name tag and shadow. If the
 * visual-only gate fails, the exact original call is replayed.
 */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.entity.Scylla_Renderer",
        remap = false)
public abstract class ScyllaRendererMixin {
    private static final String RENDER =
            "render(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/"
                    + "IABossMonsters/Scylla/Scylla_Entity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
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
    private void kusoPenguin$renderScyllaBody(@Coerce EntityModel<?> model, PoseStack poseStack,
                                              VertexConsumer buffer, int packedLight,
                                              int packedOverlay, int color,
                                              @Coerce LivingEntity entity,
                                              float entityYaw, float partialTicks,
                                              PoseStack originalPoseStack,
                                              MultiBufferSource originalBuffer,
                                              int originalLight) {
        if (!ScyllaCompatClient.renderNativePass(entity, model, poseStack, buffer,
                packedLight, packedOverlay, color, originalBuffer)) {
            model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
        }
    }
}
