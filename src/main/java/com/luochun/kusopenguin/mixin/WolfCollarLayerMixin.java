package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinHeadRenderer;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * WolfCollarLayer bypasses {@code renderColoredCutoutModel} and calls the parent model directly.
 * Redirect only that call so the native tame/invisible condition, collar colour and texture stay
 * authoritative, while a replaced prone wolf draws only the native head instead of its old body.
 */
@Mixin(WolfCollarLayer.class)
public abstract class WolfCollarLayerMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/WolfModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void kusoPenguin$renderCollarHeadOnly(WolfModel<?> model,
                                                  PoseStack poseStack, VertexConsumer buffer,
                                                  int packedLight, int packedOverlay, int color,
                                                  PoseStack originalPoseStack,
                                                  MultiBufferSource originalBuffer, int originalLight,
                                                  Wolf livingEntity, float limbSwing,
                                                  float limbSwingAmount, float partialTicks,
                                                  float ageInTicks, float netHeadYaw,
                                                  float headPitch) {
        if (PenguinRenderSupport.isProne(livingEntity)
                && PenguinHeadRenderer.renderHead(model, poseStack, buffer, packedLight,
                packedOverlay, color)) {
            return;
        }
        model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
