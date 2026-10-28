package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.ScyllaCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional Cataclysm glowing-layer bridge, gated to the exact Scylla model/entity only.
 *
 * <p>{@code LayerGenericGlowing} is shared by several Cataclysm bosses. The redirect checks the
 * exact model class, registry id, penguin effect and native visibility gate; every other user of
 * this layer keeps the complete original four-argument model call. For Scylla only the original
 * head subtree (eyes/hair) is replayed under the layer's own eye texture and implicit color.
 */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.layer.LayerGenericGlowing",
        remap = false)
public abstract class ScyllaEyeGlowLayerMixin {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V";
    private static final String NATIVE_RENDER =
            "Lnet/minecraft/client/model/EntityModel;"
                    + "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V";

    @Redirect(
            method = RENDER,
            at = @At(value = "INVOKE", target = NATIVE_RENDER),
            remap = false,
            require = 1
    )
    private void kusoPenguin$renderScyllaEyeGlow(EntityModel<?> model, PoseStack poseStack,
                                                 VertexConsumer buffer, int packedLight,
                                                 int packedOverlay,
                                                 PoseStack originalPoseStack,
                                                 MultiBufferSource originalBuffer,
                                                 int originalLight, LivingEntity entity,
                                                 float limbSwing, float limbSwingAmount,
                                                 float partialTicks, float ageInTicks,
                                                 float netHeadYaw, float headPitch) {
        // The four-argument call has no explicit color; -1 is its original white/inherit value.
        if (!ScyllaCompatClient.renderEyeGlowPass(entity, model, poseStack, buffer,
                packedLight, packedOverlay, -1)) {
            model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay);
        }
    }
}
