package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kusoPenguin$skipNonHeadArmor(PoseStack poseStack, MultiBufferSource buffer,
                                              LivingEntity entity, EquipmentSlot slot,
                                              int packedLight, HumanoidModel<?> model,
                                              float limbSwing, float limbSwingAmount,
                                              float partialTick, float ageInTicks,
                                              float netHeadYaw, float headPitch,
                                              CallbackInfo callback) {
        if (PenguinRenderSupport.shouldReplace(entity) && slot != EquipmentSlot.HEAD) {
            callback.cancel();
        }
    }
}
