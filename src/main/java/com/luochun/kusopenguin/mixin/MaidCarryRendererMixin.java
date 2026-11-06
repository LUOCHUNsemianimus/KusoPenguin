package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.luochun.kusopenguin.client.PenguinMaidPosture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optional Touhou Little Maid carry wrapper.
 *
 * <p>TLM's Bedrock renderer places a carried maid with the native humanoid-arm cue
 * {@code T(-0.375, 0.8325, 0.375) Rz(-65) Ry(+80)}. When the carrier is a transformed penguin,
 * the shared wings are shorter than the humanoid arms, so the whole carried maid is lowered and
 * moved toward the body by one explicit profile-based translation. This wrapper is above the
 * Bedrock/Geo/YSM dispatch, adds no second carry rotation and never touches the maid's native
 * animation, layers, entity position, AI or packets. Every unsupported/one-sided case returns the
 * original call with no pose operation.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer")
public abstract class MaidCarryRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @WrapMethod(
            method = RENDER,
            remap = false
    )
    private void kusoPenguin$carryCorrection(Mob maid, float entityYaw, float partialTick,
                                             PoseStack poseStack, MultiBufferSource bufferSource,
                                             int packedLight, Operation<Void> original) {
        PenguinMaidPosture.CarryCorrection correction =
                PenguinMaidPosture.carryCorrection(maid, partialTick);
        if (correction == null) {
            original.call(maid, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        poseStack.pushPose();
        try {
            poseStack.translate(correction.x(), correction.y(), correction.z());
            original.call(maid, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target =
            "Lcom/github/tartaricacid/touhoulittlemaid/client/renderer/entity/GeckoEntityMaidRenderer;" + RENDER), remap = false)
    private void kusoPenguin$carryGecko(GeckoEntityMaidRenderer renderer, Mob maid, float yaw,
                                       float partialTick, PoseStack stack, MultiBufferSource buffers,
                                       int light, Operation<Void> original) {
        stack.pushPose();
        try {
            PenguinMaidPosture.applyDelegatedCarry(stack, maid, partialTick);
            original.call(renderer, maid, yaw, partialTick, stack, buffers, light);
        } finally {
            stack.popPose();
        }
    }
}
