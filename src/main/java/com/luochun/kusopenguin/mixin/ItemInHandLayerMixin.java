package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.compat.IronCastingCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps vanilla's main/off-hand loop, display context and left-hand flag. Outside casting, only
 * the final hand translation is redirected to the current penguin wing end, exactly as before.
 *
 * <p>For an active Iron casting frame the whole {@code renderArmWithItem} body is wrapped instead.
 * PlayerAnimator itself injects an arm bend and an item offset after
 * {@code ArmedModel.translateToHand}; bypassing the original avoids both its HEAD and tail item
 * bends. The live item is drawn through the existing {@link ItemInHandRenderer} with the shared
 * penguin hand frame and vanilla's exact XP(-90), YP(180), translate(armSign/16, .125, -.625)
 * tail, while the outer loop and display context stay native.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

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

    @WrapMethod(method = "renderArmWithItem")
    private void kusoPenguin$renderCastingHand(LivingEntity entity, ItemStack itemStack,
                                               ItemDisplayContext displayContext, HumanoidArm arm,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int packedLight, Operation<Void> original) {
        // Sample the whole native item channel before touching the pose. Null means the original
        // method keeps complete ownership, exactly as for an inactive Iron layer.
        IronCastingCompatClient.ItemChannel channel =
                PenguinRenderContext.castingItemChannel(arm);
        if (itemStack.isEmpty() || channel == null) {
            original.call(entity, itemStack, displayContext, arm, poseStack, buffer, packedLight);
            return;
        }
        poseStack.pushPose();
        try {
            if (!PenguinRenderContext.applyHand(poseStack, arm)) {
                // The shared hand frame is unavailable; keep complete native ownership.
                original.call(entity, itemStack, displayContext, arm, poseStack, buffer,
                        packedLight);
                return;
            }
            boolean left = arm == HumanoidArm.LEFT;
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            // Native HeldItemMixin.changeItemLocation injects immediately before renderItem, after
            // this vanilla tail. Reuse that exact final channel once; no further bend is added.
            IronCastingCompatClient.applyItemTransform(poseStack, channel);
            this.itemInHandRenderer.renderItem(entity, itemStack, displayContext, left,
                    poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
