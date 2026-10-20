package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarriedBlockLayer.class)
public abstract class CarriedBlockLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/monster/EnderMan;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void kusoPenguin$hideProp(PoseStack pose, MultiBufferSource buffers, int light,
                                     EnderMan entity, float swing, float amount, float partial,
                                     float age, float yaw, float pitch, CallbackInfo ci) {
        if (PenguinRenderSupport.shouldReplace(entity)) ci.cancel();
    }
}
