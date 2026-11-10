package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.luochun.kusopenguin.client.vocals.MicrophonePerformance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;"
                    + "FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void kusoPenguin$microphoneSway(EntityRenderer<?> renderer, Entity entity, float yaw,
                                           float partialTick, PoseStack stack, MultiBufferSource buffers,
                                           int light, Operation<Void> original) {
        if (entity instanceof LivingEntity living) {
            MicrophonePerformance.render(living, partialTick, stack,
                    () -> original.call(renderer, entity, yaw, partialTick, stack, buffers, light));
        } else {
            original.call(renderer, entity, yaw, partialTick, stack, buffers, light);
        }
    }
}
