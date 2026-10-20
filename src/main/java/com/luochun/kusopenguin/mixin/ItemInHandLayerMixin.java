package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps vanilla's main/off-hand loop, display context, left-hand flag and interaction pose, and
 * only redirects the final hand translation to the current penguin wing end.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Redirect(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void kusoPenguin$mountOnWing(ArmedModel model, HumanoidArm arm, PoseStack poseStack) {
        if (!PenguinRenderContext.applyHand(poseStack, arm)) {
            model.translateToHand(arm, poseStack);
        }
    }
}
