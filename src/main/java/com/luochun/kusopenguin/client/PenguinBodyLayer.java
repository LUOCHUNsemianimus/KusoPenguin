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
 *
 * <p>Upright families keep their adapter/profile transform. The seven prone animals use one
 * whole-assembly transform from {@link PenguinPronePose}: translating and rotating only the torso
 * would leave wings and feet standing through the ground.
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
        boolean prone = PenguinRenderSupport.isProne(livingEntity);
        boolean dancing = PenguinRenderContext.isDanceFrame(livingEntity);
        PenguinPoseAdapter adapter = prone ? null : PenguinPoseAdapters.find(this.getParentModel());
        PenguinProportions.Profile profile = prone ? null
                : PenguinRenderContext.profile(this.getParentModel());
        if (!prone && ((adapter == null && !dancing) || profile == null)) {
            return;
        }

        PenguinRenderContext.prepareBody(this.penguin);
        PenguinMeshModel mesh = PenguinMeshPreview.isEnabled()
                ? PenguinMeshModel.current()
                : null;
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
                mesh != null ? PenguinMeshModel.TEXTURE : PenguinBodyModel.TEXTURE));
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F);
        poseStack.pushPose();
        try {
            if (prone) {
                // Single transform for all five roots: offset from the measured floor bound,
                // family scale, then the +90 degree X lay-down rotation.
                PenguinPronePose.applyBodyTransform(poseStack, this.penguin,
                        this.getParentModel(), mesh);
            } else {
                // Each family has an explicit neck and floor anchor; head shift and body offset
                // differ when the native head is not attached at model Y=0.
                poseStack.translate(0.0F, profile.bodyOffsetBlocks(), 0.0F);
                poseStack.scale(profile.bodyScale(), profile.bodyScale(), profile.bodyScale());
            }
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
