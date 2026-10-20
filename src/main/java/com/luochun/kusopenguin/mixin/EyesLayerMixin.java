package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinHeadRenderer;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EyesLayer.class)
public abstract class EyesLayerMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void kusoPenguin$headOnly(EntityModel<?> model, PoseStack pose, VertexConsumer consumer,
                                     int light, int overlay, PoseStack originalPose,
                                     MultiBufferSource buffers, int originalLight, Entity living,
                                     float swing, float amount, float partial, float age,
                                     float yaw, float pitch) {
        if (living instanceof LivingEntity entity && PenguinRenderSupport.shouldReplace(entity)
                && PenguinHeadRenderer.renderHead(model, pose, consumer, light, overlay, -1)) return;
        model.renderToBuffer(pose, consumer, light, overlay);
    }
}
