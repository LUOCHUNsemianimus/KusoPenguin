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

        PenguinRenderContext.prepareBody(this.penguin);
        PenguinMeshModel mesh = PenguinMeshPreview.isEnabled()
                ? PenguinMeshModel.current()
                : null;
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                mesh != null ? PenguinMeshModel.TEXTURE : PenguinBodyModel.TEXTURE));
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F);
        // Each family has an explicit neck and floor anchor; head shift and body offset
        // differ when the native head is not attached at model Y=0.
        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, profile.bodyOffsetBlocks(), 0.0F);
            poseStack.scale(profile.bodyScale(), profile.bodyScale(), profile.bodyScale());
            PenguinAnimationPose animated=PenguinRenderContext.pose();
            if(mesh!=null&&animated!=null){
                PenguinMeshModel.currentSkin().render(poseStack,vertexConsumer,packedLight,packedOverlay,-1,animated);
            } else if (mesh != null) {
                mesh.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1, this.penguin);
            } else {
                this.penguin.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, -1);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
