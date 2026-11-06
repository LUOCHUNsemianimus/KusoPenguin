package com.luochun.kusopenguin.mixin;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.luochun.kusopenguin.client.PenguinMaidPosture;
import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional YSM entry frame around TLM's maid geo-render delegation.
 *
 * <p>TLM's {@code EntityMaidRenderer.render(Mob, ...)} hands the whole frame to the nullable
 * {@code ysmMaidRenderer} field through {@code IGeoEntityRenderer.geoRender}. This redirect wraps
 * that single invocation call in one nestable penguin render context with a real
 * {@code try/finally}, so the native body pass, every native layer and the deferred shared body
 * share one evaluated pose and the context is popped on every return or exception.
 *
 * <p>The body is intentionally NOT drawn here. {@link YsmCompatClient#exit(boolean)} draws it
 * after the native delegation returned, so switching to the penguin texture cannot flush and
 * invalidate a buffer that the native mesh is still writing.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer")
public abstract class MaidYsmEntryMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String GEO_RENDER =
            "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/IGeoEntityRenderer;"
                    + "geoRender(Lnet/minecraft/world/entity/Entity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Redirect(
            method = RENDER,
            at = @At(value = "INVOKE", target = GEO_RENDER),
            remap = false,
            require = 1
    )
    private void kusoPenguin$wrapYsmFrame(IGeoEntityRenderer<Entity> nativeRenderer,
                                          Entity entity, float entityYaw, float partialTick,
                                          PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight) {
        poseStack.pushPose();
        try {
            PenguinMaidPosture.applyDelegatedCarry(poseStack, entity, partialTick);
            YsmCompatClient.enter(this, entity, poseStack, bufferSource, packedLight, partialTick);
            boolean normal = false;
            try {
                nativeRenderer.geoRender(entity, entityYaw, partialTick, poseStack, bufferSource,
                        packedLight);
                normal = true;
            } finally {
                YsmCompatClient.exit(normal);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
