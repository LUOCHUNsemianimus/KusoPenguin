package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.luochun.kusopenguin.client.compat.IronHumanoidCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Optional suppression of body/limb armor geometry for an active native Iron caster frame. The
 * helmet bone is deliberately kept, so head armor retains its native anchor through the head
 * reanchor. No other layer or bone is cancelled.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer")
public abstract class IronGeoArmorLayerMixin {
    @WrapMethod(
            method = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/LivingEntity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V"
    )
    private void kusoPenguin$suppressBodyArmor(PoseStack poseStack, LivingEntity entity,
                                               GeoBone bone, RenderType renderType,
                                               MultiBufferSource buffers, VertexConsumer buffer,
                                               float partialTick, int packedLight,
                                               int packedOverlay, Operation<Void> original) {
        if (IronHumanoidCompatClient.shouldSuppressArmorBone(entity, bone)) {
            return;
        }
        original.call(poseStack, entity, bone, renderType, buffers, buffer, partialTick,
                packedLight, packedOverlay);
    }
}
