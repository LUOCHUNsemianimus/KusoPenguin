package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinHeadRenderer;
import com.luochun.kusopenguin.client.PenguinProportions;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    public abstract EntityModel<?> getModel();

    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
    private void kusoPenguin$beginPose(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci){
        PenguinRenderContext.push(entity,this.getModel(),partial);
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void kusoPenguin$endPose(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci){
        PenguinRenderContext.pop();
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void kusoPenguin$renderOriginalHeadOnly(EntityModel<?> model, PoseStack poseStack,
                                                    VertexConsumer buffer, int packedLight,
                                                    int packedOverlay, int color,
                                                    LivingEntity entity, float entityYaw,
                                                    float partialTicks, PoseStack originalPoseStack,
                                                    MultiBufferSource originalBuffer, int originalLight) {
        if (PenguinRenderSupport.shouldReplace(entity)
                && PenguinHeadRenderer.renderHead(model, poseStack, buffer, packedLight,
                packedOverlay, color)) {
            return;
        }
        model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }

    /**
     * Head-following vanilla layers that build their own model parts need the same single head
     * translation as the main head. PenguinBodyLayer scales itself, and the villager profession
     * layer goes through PenguinHeadRenderer, so neither may be wrapped here.
     */
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"
            )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void kusoPenguin$offsetHeadLayer(RenderLayer layer, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight,
                                             Entity layerEntity, float limbSwing,
                                             float limbSwingAmount, float partialTick,
                                             float ageInTicks, float netHeadYaw, float headPitch,
                                             LivingEntity entity, float entityYaw,
                                             float renderPartialTick, PoseStack originalPoseStack,
                                             MultiBufferSource originalBuffer, int originalLight) {
        // Body-only overlays are incompatible with the horizontal body. Suppress them only while
        // the prone replacement is active; effect removal restores the complete vanilla layers.
        if ((PenguinRenderSupport.isProne(entity) || PenguinRenderContext.isDanceFrame(entity))
                && (layer instanceof SaddleLayer<?, ?> || layer instanceof WolfArmorLayer)) {
            return;
        }
        if (layer instanceof ElytraLayer<?, ?>) {
            // Keep the native ElytraLayer (equipment check, textures/cape overrides, glint and
            // wing animation) but re-anchor its pose on the penguin back for a replaced gliding
            // player. For every other draw the helper returns false and the original path below
            // stays in charge, including the hidden-Elytra policy for non-gliding replacements.
            poseStack.pushPose();
            boolean mounted = PenguinRenderContext.mountElytraOnBack(poseStack);
            if (mounted) {
                try {
                    layer.render(poseStack, buffer, packedLight, layerEntity, limbSwing,
                            limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
                } finally {
                    poseStack.popPose();
                }
                return;
            }
            poseStack.popPose();
        }
        PenguinProportions.Profile profile = PenguinRenderSupport.shouldReplace(entity)
                && isOffsetHeadLayer(layer)
                ? PenguinRenderContext.profile(this.getModel())
                : null;
        if (profile == null) {
            layer.render(poseStack, buffer, packedLight, layerEntity, limbSwing,
                    limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        poseStack.pushPose();
        try {
            PenguinRenderContext.applyHead(poseStack);
            poseStack.translate(0.0F, profile.headDropBlocks(), 0.0F);
            layer.render(poseStack, buffer, packedLight, layerEntity, limbSwing,
                    limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean isOffsetHeadLayer(RenderLayer<?, ?> layer) {
        return layer instanceof CustomHeadLayer
                || layer instanceof Deadmau5EarsLayer
                || layer instanceof HumanoidArmorLayer;
    }
}
