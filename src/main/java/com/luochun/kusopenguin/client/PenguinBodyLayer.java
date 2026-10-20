package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws the five-part penguin body after the vanilla head-only main pass. A single shared model
 * instance is reset on every draw so one entity cannot leak poses into the next.
 */
public final class PenguinBodyLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    private final PenguinBodyModel penguin;

    public PenguinBodyLayer(RenderLayerParent<T, M> renderer, PenguinBodyModel penguin) {
        super(renderer);
        this.penguin = penguin;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       T livingEntity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (!PenguinRenderSupport.shouldReplace(livingEntity)) {
            return;
        }
        PenguinPoseAdapter adapter = PenguinPoseAdapters.find(this.getParentModel());
        PenguinProportions.Profile profile = PenguinProportions.forModel(this.getParentModel());
        if (adapter == null || profile == null) {
            return;
        }

        this.penguin.resetPose();
        adapter.apply(this.penguin, this.getParentModel(), livingEntity);
        PenguinMeshModel mesh = PenguinMeshPreview.isEnabled()
                ? PenguinMeshModel.current()
                : null;
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                mesh != null ? PenguinMeshModel.TEXTURE : PenguinBodyModel.TEXTURE));
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F);
        // Equivalent to scaling around the original foot plane Y=24: translate then scale maps
        // a model-space point p to s*p + (0, headDrop, 0), so the feet stay at Y=24.
        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, profile.headDropBlocks(), 0.0F);
            poseStack.scale(profile.bodyScale(), profile.bodyScale(), profile.bodyScale());
            if (mesh != null) {
                mesh.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1, this.penguin);
            } else {
                this.penguin.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, -1);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
