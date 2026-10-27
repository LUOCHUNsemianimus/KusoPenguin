package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional YSM 2.6.5 native mesh interception.
 *
 * <p>The pinned static utility draws the already CPU-skinned native maid mesh into a
 * {@link VertexConsumer}. At HEAD this hook asks {@link YsmCompatClient} to capture the full and
 * AllHead-hidden passes with an identity pose, subtract the exact body quad multiset and replay
 * only the remaining head/hair quads. Cancelling here removes the native body draw only for the
 * current material pass; the shared penguin body is drawn later by the entry frame.
 *
 * <p>All method names/descriptors are the pinned 2.6.5 obfuscated contract. {@code require = 0}
 * means an unsupported YSM version simply keeps the complete native draw and logs once.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OoooOoo0o0000O0o00o0O0Oo")
public abstract class MaidYsmNativeMeshMixin {
    private static final String DRAW =
            "oOo0OO0O0o000OO0O000oo0o(Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/elfmcys/yesstevemodel/O0OOo0OoOoo0o0o0oO000o00;"
                    + "[F[FIIIIFFFF)V";

    @Inject(method = DRAW, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void kusoPenguin$captureNativeMesh(VertexConsumer output, PoseStack.Pose pose,
                                                      @Coerce Object metadata,
                                                      float[] boneParameters, float[] auxiliary,
                                                      int textureSelector, int mode,
                                                      int packedLight, int packedOverlay,
                                                      float red, float green, float blue,
                                                      float alpha, CallbackInfo ci) {
        if (YsmCompatClient.capturePass(output, pose, metadata, boneParameters, auxiliary,
                textureSelector, mode, packedLight, packedOverlay, red, green, blue, alpha)) {
            ci.cancel();
        }
    }
}
