package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinHeadRenderer;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.luochun.kusopenguin.client.compat.IgnisCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin {
    @Redirect(
            method = "renderColoredCutoutModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private static void kusoPenguin$renderLayerHeadOnly(EntityModel<?> model, PoseStack poseStack,
                                                        VertexConsumer buffer, int packedLight,
                                                        int packedOverlay, int color,
                                                        EntityModel<?> originalModel,
                                                        ResourceLocation textureLocation,
                                                        PoseStack originalPoseStack,
                                                        MultiBufferSource originalBuffer,
                                                        int originalLight, LivingEntity entity,
                                                        int originalColor) {
        if (IgnisCompatClient.renderArmorPass(entity,model,poseStack,buffer,packedLight,
                packedOverlay,color)) {
            return;
        }
        if (PenguinRenderSupport.shouldReplace(entity)
                && PenguinHeadRenderer.renderHead(model, poseStack, buffer, packedLight,
                packedOverlay, color)) {
            return;
        }
        model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
