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
 *
 * <p>{@link #renderCanonicalBody} is the single canonical body entry point. The optional Gecko
 * renderer calls it after applying the vanilla model flip, so both native Gecko head rendering
 * and the shared body reuse exactly the same profile, pose evaluation and mesh drawing code.
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
        if (!prone) {
            // Canonical upright body used by the vanilla head-only pass and the Gecko renderer.
            renderCanonicalBody(poseStack, buffer, packedLight, livingEntity,
                    this.getParentModel());
            return;
        }

        PenguinRenderContext.prepareBody(this.penguin);
        PenguinMeshModel mesh = PenguinMeshModel.current();
        if (mesh == null) {
            // No complete mesh yet: leave the native body/head pass untouched instead of masking it.
            return;
        }
        VertexConsumer vertexConsumer = bodyBuffer(buffer);
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F);
        poseStack.pushPose();
        try {
            // Single transform for all five roots: offset from the measured floor bound,
            // family scale, then the +90 degree X lay-down rotation.
            PenguinPronePose.applyBodyTransform(poseStack, this.penguin,
                    this.getParentModel(), mesh);
            drawBody(poseStack, vertexConsumer, packedLight, packedOverlay, this.penguin, mesh);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Draws the prepared shared body in the canonical penguin model frame (Y-down, floor Y=24).
     * The caller owns the canonical frame: vanilla layers receive it from
     * {@code LivingEntityRenderer}; the Gecko bridge applies C explicitly. This method adds the
     * profile body offset/scale and owns its own push/pop.
     */
    public static void renderCanonicalBody(PoseStack poseStack, MultiBufferSource buffer,
                                           int packedLight, LivingEntity entity,
                                           EntityModel<?> originalModel) {
        PenguinBodyModel penguin = PenguinRenderContext.sharedModel();
        PenguinProportions.Profile profile = PenguinRenderContext.profile(originalModel);
        PenguinMeshModel mesh = PenguinMeshModel.current();
        if (penguin == null || profile == null || mesh == null) {
            // A missing mesh must not suppress or replace the native body draw.
            return;
        }
        PenguinRenderContext.prepareBody(penguin);
        VertexConsumer vertexConsumer = bodyBuffer(buffer);
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        poseStack.pushPose();
        try {
            // Each family has an explicit neck and floor anchor; head shift and body offset
            // differ when the native head is not attached at model Y=0.
            poseStack.translate(0.0F, profile.bodyOffsetBlocks(), 0.0F);
            poseStack.scale(profile.bodyScale(), profile.bodyScale(), profile.bodyScale());
            drawBody(poseStack, vertexConsumer, packedLight, packedOverlay, penguin, mesh);
        } finally {
            poseStack.popPose();
        }
    }

    private static VertexConsumer bodyBuffer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entityCutoutNoCull(PenguinMeshModel.TEXTURE));
    }

    private static void drawBody(PoseStack poseStack, VertexConsumer vertexConsumer,
                                 int packedLight, int packedOverlay, PenguinBodyModel penguin,
                                 PenguinMeshModel mesh) {
        PenguinAnimationPose animated = PenguinRenderContext.pose();
        if (animated != null) {
            PenguinSkinnedMesh skin = PenguinMeshModel.skinFor(mesh);
            if (skin == null) {
                throw new IllegalStateException("Animated penguin mesh is missing its baked skin");
            }
            skin.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1, animated);
        } else if (mesh.hasExplicitBindings()) {
            // Schema 2 outside action clips: keep the prepared five native roots and render the
            // weighted skin with pelvis-pinned seam vertices, so ordinary walking/crouch/casting
            // fade/prone cannot separate the authored leg seam from the torso.
            PenguinSkinnedMesh skin = PenguinMeshModel.skinFor(mesh);
            PenguinAnimationClip bind = PenguinMeshModel.animationClip();
            if (skin == null || bind == null) {
                throw new IllegalStateException("Weighted penguin mesh is missing its baked skin");
            }
            skin.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1,
                    PenguinAnimationPose.nativeBodyOnly(penguin, bind));
        } else {
            mesh.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1, penguin);
        }
    }
}
