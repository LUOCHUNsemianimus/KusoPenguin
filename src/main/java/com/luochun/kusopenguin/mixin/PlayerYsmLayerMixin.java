package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Optional YSM player-layer interception.
 *
 * <p>YSM's four player layers are its own objects, not vanilla {@code RenderLayer}s. This wraps the
 * single layer callback in the shared base layer loop. For an active player penguin frame the call
 * is queued with a copy of the pose; the frame replays every queued callback once after the native
 * mesh returned. A maid frame (which is handled by the TLM layer bridge) and every non-player draw
 * keep the untouched native call.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo")
public abstract class PlayerYsmLayerMixin {
    private static final String LAYER_LOOP =
            "oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;"
                    + "Lcom/elfmcys/yesstevemodel/OoooO0Ooo0O0o0OO0ooOOooO;)V";
    private static final String LAYER_CALL =
            "Lcom/elfmcys/yesstevemodel/O000o00OooO00oO0oo0o0o0o;"
                    + "oOo0OO0O0o000OO0O000oo0o(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;FFFFFF)V";

    @WrapOperation(
            method = LAYER_LOOP,
            at = @At(value = "INVOKE", target = LAYER_CALL),
            remap = false,
            require = 1
    )
    private void kusoPenguin$queuePlayerLayer(@Coerce Object layer, PoseStack poseStack,
                                              MultiBufferSource bufferSource, int packedLight,
                                              @Coerce Object model, float limbSwing,
                                              float limbSwingAmount, float partialTick,
                                              float ageInTicks, float netHeadYaw, float headPitch,
                                              Operation<Void> original) {
        if (YsmCompatClient.playerLayer(layer, poseStack, bufferSource, packedLight, model,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch)) {
            return;
        }
        original.call(layer, poseStack, bufferSource, packedLight, model, limbSwing,
                limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
