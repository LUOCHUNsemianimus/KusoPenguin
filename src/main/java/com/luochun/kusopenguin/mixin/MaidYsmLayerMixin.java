package com.luochun.kusopenguin.mixin;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.compat.MaidGeoCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional YSM maid layer hook.
 *
 * <p>YSM's maid renderer walks the same TLM {@link GeoLayerRenderer} list as the Gecko renderer.
 * Only after the native quad proof succeeded ({@code frame.ready}): the backpack/back-item/banner
 * layers authored for the native torso are kept out of the shared penguin body, while the native
 * head-gear layer is conjugated with the canonical head movement
 * {@code S * C * Delta * Tdrop * C^-1} so helmets/skulls stay on the retained native head. On any
 * fallback every layer renders unchanged.
 *
 * <p>The YSM renderer class is version-pinned and referenced by name. The @Pseudo target is skipped
 * when that class is absent, but the pinned callback hook itself is required once present.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.ooOoooo0o000Oo0OoOooo0oo")
public abstract class MaidYsmLayerMixin {
    private static final String CALLBACK =
            "oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/OooO0Oo0oO00oo0Ooo0O0O00;"
                    + "FLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;"
                    + "Lcom/elfmcys/yesstevemodel/OoooO0Ooo0O0o0OO0ooOOooO;)V";
    private static final String LAYER_RENDER =
            "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/GeoLayerRenderer;"
                    + "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/entity/Entity;FFFFFF)V";

    @Redirect(
            method = CALLBACK,
            at = @At(value = "INVOKE", target = LAYER_RENDER),
            remap = false,
            require = 1
    )
    private void kusoPenguin$renderYsmLayer(GeoLayerRenderer layer,
                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, Entity entity, float limbSwing,
                                            float limbSwingAmount, float partialTick,
                                            float ageInTicks, float netHeadYaw, float headPitch) {
        EntityModel<?> view = PenguinRenderContext.currentModel();
        if (entity instanceof LivingEntity living
                && MaidGeoCompatClient.isReplacement(view, living)) {
            if (MaidGeoCompatClient.isGeoDecorationLayer(layer)) {
                // Authored for the native torso; it would float on the shared penguin body.
                return;
            }
            if (MaidGeoCompatClient.isGeoHeadLayer(layer)) {
                poseStack.pushPose();
                try {
                    PenguinRenderContext.applyGeoHead(poseStack);
                    layer.render(poseStack, bufferSource, packedLight, entity, limbSwing,
                            limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
                } finally {
                    poseStack.popPose();
                }
                return;
            }
        }
        layer.render(poseStack, bufferSource, packedLight, entity, limbSwing, limbSwingAmount,
                partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
