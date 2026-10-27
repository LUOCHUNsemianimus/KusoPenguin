package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.luochun.kusopenguin.client.compat.IronHumanoidCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optional Iron HumanoidRenderer scope. The hard-coded body cape is drawn after the Geo renderer
 * returns, so it must share the same whitelist/effect/ready decision as the penguin replacement.
 * This wrapper also keeps the decision scoped to one renderer call and never affects head/FX layers.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.mobs.HumanoidRenderer")
public abstract class IronHumanoidRendererMixin {
    @WrapMethod(
            method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
    )
    private void kusoPenguin$humanoidScope(Mob entity, float yaw, float partialTick,
                                           PoseStack poseStack, MultiBufferSource buffers,
                                           int packedLight, Operation<Void> original) {
        IronHumanoidCompatClient.beginHumanoidRender(entity);
        try {
            original.call(entity, yaw, partialTick, poseStack, buffers, packedLight);
        } finally {
            IronHumanoidCompatClient.endHumanoidRender();
        }
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"
            )
    )
    private void kusoPenguin$suppressBodyCape(RenderLayer<?, ?> layer, PoseStack poseStack,
                                              MultiBufferSource buffers, int packedLight,
                                              Entity entity, float limbSwing,
                                              float limbSwingAmount, float partialTick,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              Operation<Void> original) {
        if (IronHumanoidCompatClient.shouldSuppressCape(entity)) {
            return;
        }
        original.call(layer, poseStack, buffers, packedLight, entity, limbSwing,
                limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
