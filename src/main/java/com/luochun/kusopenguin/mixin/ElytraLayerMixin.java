package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kusoPenguin$hideElytra(PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, LivingEntity entity, float limbSwing,
                                        float limbSwingAmount, float partialTick, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo callback) {
        // A replaced player that is actually gliding is re-mounted by LivingEntityRendererMixin
        // and must keep the native layer. Everything else replaced (including detached death
        // copies and non-gliding states) keeps the previous hidden-Elytra policy.
        if (PenguinRenderSupport.shouldReplace(entity)
                && !PenguinRenderSupport.shouldMountElytraOnBack(entity)) {
            callback.cancel();
        }
    }
}
