package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParrotOnShoulderLayer.class)
public abstract class ParrotOnShoulderLayerMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kusoPenguin$hideShoulderParrot(PoseStack poseStack, MultiBufferSource buffer,
                                                int packedLight, Player entity,
                                                float limbSwing, float limbSwingAmount,
                                                float partialTick, float ageInTicks,
                                                float netHeadYaw, float headPitch,
                                                CallbackInfo callback) {
        if (PenguinRenderSupport.shouldReplace(entity)) {
            callback.cancel();
        }
    }
}
