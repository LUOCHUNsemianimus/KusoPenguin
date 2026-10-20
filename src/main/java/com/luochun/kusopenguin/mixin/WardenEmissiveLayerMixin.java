package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinHeadRenderer;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.WardenEmissiveLayer;
import net.minecraft.world.entity.monster.warden.Warden;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WardenEmissiveLayer.class)
public abstract class WardenEmissiveLayerMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/monster/warden/Warden;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/WardenModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void kusoPenguin$headOnly(WardenModel<?> model, PoseStack pose, VertexConsumer consumer,
                                     int light, int overlay, int color, PoseStack originalPose,
                                     MultiBufferSource buffers, int originalLight, Warden living,
                                     float swing, float amount, float partial, float age,
                                     float yaw, float pitch) {
        if (PenguinRenderSupport.shouldReplace(living)
                && PenguinHeadRenderer.renderHead(model, pose, consumer, light, overlay, color)) return;
        model.renderToBuffer(pose, consumer, light, overlay, color);
    }
}
