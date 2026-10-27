package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.luochun.kusopenguin.client.compat.IronHumanoidCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Optional Gecko4 bridge for the seven ordinary Iron humanoids.
 *
 * <p>The native renderer still performs its complete hierarchy traversal and matrix tracking. This
 * mixin only (a) pushes the one-draw penguin view at the exact point after native animations and
 * scale transforms, (b) skips native cube submission outside the head subtree while that view is
 * ready, and (c) reanchors the head and hand locators with a scoped local matrix. Native item/
 * helmet/FX layers still run from their original code.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoEntityRenderer")
public abstract class IronGeoEntityRendererMixin {
    @WrapOperation(
            method = "actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V"
            )
    )
    private void kusoPenguin$nativeCasterRender(GeoEntityRenderer<?> renderer,
                                                PoseStack poseStack, GeoAnimatable animatable,
                                                BakedGeoModel model, RenderType renderType,
                                                MultiBufferSource buffers, VertexConsumer buffer,
                                                boolean isReRender, float partialTick,
                                                int packedLight, int packedOverlay, int color,
                                                Operation<Void> original) {
        // The wrapped GeoRenderer target receives GeoAnimatable even though the enclosing
        // GeoEntityRenderer overload is Entity. Extract the concrete entity through the
        // intersection contract; do not force-cast foreign animatables.
        Entity entity = animatable instanceof Entity candidate ? candidate : null;
        boolean active = false;
        try {
            active = entity != null && IronHumanoidCompatClient.begin(entity, model, poseStack,
                    buffers, packedLight, partialTick, isReRender);
            // Pass the original native argument unchanged so the wrapped call keeps its exact
            // descriptor and runtime type.
            original.call(renderer, poseStack, animatable, model, renderType, buffers, buffer,
                    isReRender, partialTick, packedLight, packedOverlay, color);
        } finally {
            if (active) {
                IronHumanoidCompatClient.end();
            }
        }
    }

    @WrapMethod(
            method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V"
    )
    private void kusoPenguin$nativeBone(PoseStack poseStack, Entity entity, GeoBone bone,
                                        RenderType renderType, MultiBufferSource buffers,
                                        VertexConsumer buffer, boolean isReRender,
                                        float partialTick, int packedLight, int packedOverlay,
                                        int color, Operation<Void> original) {
        // Scoped pre-parent adjustment: the native recursion still adds its own local matrix, and
        // the pop prevents the head/hand frame leaking into sibling bones.
        poseStack.pushPose();
        try {
            IronHumanoidCompatClient.adjustHead(entity, poseStack, bone);
            IronHumanoidCompatClient.adjustHand(entity, poseStack, bone);
            original.call(poseStack, entity, bone, renderType, buffers, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, color);
        } finally {
            poseStack.popPose();
        }
    }

    @WrapOperation(
            method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/renderer/GeoEntityRenderer;renderCubesOfBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void kusoPenguin$skipNativeBodyCubes(GeoEntityRenderer<?> renderer,
                                                 PoseStack poseStack, GeoBone bone,
                                                 VertexConsumer buffer, int packedLight,
                                                 int packedOverlay, int color,
                                                 Operation<Void> original,
                                                 @Local(argsOnly = true) Entity entity) {
        // Identity-gated: a nested Geo render of another entity must not inherit the parent's
        // body filtering even though the enclosing renderRecursively marker is Entity-based.
        if (IronHumanoidCompatClient.shouldSkipCubes(entity, bone)) {
            return;
        }
        original.call(renderer, poseStack, bone, buffer, packedLight, packedOverlay, color);
    }
}
