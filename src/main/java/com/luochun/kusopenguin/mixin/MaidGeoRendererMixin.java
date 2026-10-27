package com.luochun.kusopenguin.mixin;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.EModelRenderCycle;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.luochun.kusopenguin.client.compat.MaidGeoCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Touhou Little Maid Gecko replacement bridge.
 *
 * <p>TLM's {@code GeckoEntityMaidRenderer} overrides the whole vanilla living render path, so a
 * {@code RenderLayer} addition alone can never replace it. This pseudo mixin hooks the actual
 * 7-argument {@code GeoReplacedEntityRenderer.render(LivingEntity, AnimatableEntity, ...)} draw:
 * <ol>
 *   <li>opens one render context for the native model pass and every native Geo layer;</li>
 *   <li>replaces the native model draw with the canonical body plus the real native head subtree;</li>
 *   <li>suppresses only the native backpack/back-item/banner layers and re-applies the complete
 *       canonical head delta to the native head-gear layer.</li>
 * </ol>
 *
 * <p>The native {@code renderEarly} scale is called exactly once and the render cycle is advanced to
 * {@code REPEATED}, so external model scale, texture, light, overlay and layer frame stay native.
 * When no supported head exists the original draw runs unchanged.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer")
public abstract class MaidGeoRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/core/AnimatableEntity;"
                    + "FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String NATIVE_MODEL_RENDER =
            "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/GeoReplacedEntityRenderer;"
                    + "render(Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/animated/"
                    + "AnimatedGeoModel;Ljava/lang/Object;FLnet/minecraft/client/renderer/"
                    + "RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/"
                    + "renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "IIFFFF)V";

    /**
     * The actual body/layer pass is the 7-argument overload. The 6-argument overload only delegates
     * to it, so targeting that stub would never intercept a drawing call.
     */
    @Inject(method = RENDER, at = @At("HEAD"), remap = false)
    private void kusoPenguin$beginGeoFrame(LivingEntity entity, AnimatableEntity<?> animatable,
                                           float entityYaw, float partialTick, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight,
                                           CallbackInfo ci) {
        EntityModel<?> model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        if (PenguinRenderSupport.shouldReplace(entity)) {
            EntityModel<?> view = MaidGeoCompatClient.createView(entity, animatable);
            if (view != null && MaidGeoCompatClient.isReplacement(view, entity)) {
                model = view;
            }
        }
        PenguinRenderContext.push(entity, model, partialTick);
    }

    @Inject(method = RENDER, at = @At("RETURN"), remap = false)
    private void kusoPenguin$endGeoFrame(LivingEntity entity, AnimatableEntity<?> animatable,
                                         float entityYaw, float partialTick, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight,
                                         CallbackInfo ci) {
        PenguinRenderContext.pop();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = NATIVE_MODEL_RENDER),
            remap = false)
    private void kusoPenguin$renderGeoModel(GeoReplacedEntityRenderer<LivingEntity, ?> renderer,
                                            AnimatedGeoModel model, Object animatable,
                                            float partialTick, RenderType renderType,
                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                            VertexConsumer buffer, int packedLight,
                                            int packedOverlay, float red, float green, float blue,
                                            float alpha) {
        LivingEntity entity = (LivingEntity) animatable;
        EntityModel<?> view = PenguinRenderContext.currentModel();
        if (view == null || !MaidGeoCompatClient.isReplacement(view, entity)) {
            renderer.render(model, entity, partialTick, renderType, poseStack, bufferSource,
                    buffer, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        // Mirror IGeoRenderer.render's native cycle exactly once, then advance to REPEATED so the
        // native Geo layers keep the same scaled frame and do not apply renderEarly again.
        renderer.setCurrentRTB(bufferSource);
        renderer.renderEarly(entity, poseStack, partialTick, bufferSource, buffer, packedLight,
                packedOverlay, red, green, blue, alpha);
        renderer.renderLate(entity, poseStack, partialTick, bufferSource, buffer, packedLight,
                packedOverlay, red, green, blue, alpha);
        try {
            MaidGeoCompatClient.renderReplacement(view, renderer, model, entity, poseStack,
                    bufferSource, renderType, buffer, packedLight, packedOverlay, red, green, blue,
                    alpha);
        } finally {
            renderer.setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
        }
    }

    @Redirect(
            method = RENDER,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/"
                            + "GeoLayerRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/"
                            + "world/entity/Entity;FFFFFF)V"
            ),
            remap = false
    )
    private void kusoPenguin$renderGeoLayer(GeoLayerRenderer layer, PoseStack poseStack,
                                            MultiBufferSource bufferSource, int packedLight,
                                            Entity entity, float limbSwing, float limbSwingAmount,
                                            float partialTick, float ageInTicks, float netHeadYaw,
                                            float headPitch) {
        EntityModel<?> view = PenguinRenderContext.currentModel();
        if (view != null && entity instanceof LivingEntity living
                && MaidGeoCompatClient.isReplacement(view, living)) {
            if (MaidGeoCompatClient.isGeoDecorationLayer(layer)) {
                // Authored for the native torso; it would float on the shared penguin body.
                return;
            }
            if (MaidGeoCompatClient.isGeoHeadLayer(layer)) {
                // The layer walks native Gecko head locators. Conjugate the same canonical
                // Delta * Tdrop used by the main head pass into that native frame.
                poseStack.pushPose();
                try {
                    PenguinRenderContext.applyGeoHead(poseStack);
                    layer.render(poseStack, bufferSource, packedLight, entity, limbSwing,
                            limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
                } finally {
                    poseStack.popPose();
                }
                return;
            }
        }
        layer.render(poseStack, bufferSource, packedLight, entity, limbSwing, limbSwingAmount,
                partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
