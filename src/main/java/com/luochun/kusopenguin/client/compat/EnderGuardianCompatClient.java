package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinMeshModel;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRig;
import com.luochun.kusopenguin.client.PenguinSkinnedMesh;
import com.luochun.kusopenguin.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Optional visual-only Cataclysm 1.21.1-3.33 Ender Guardian bridge.
 *
 * <p>Only the rendered body is replaced: AI, damage, AABB, animations, phase and death stay in the
 * native renderer. The complete native helmet subtree and both native fists are retained under the
 * original light/overlay/color and phase visibility. No Cataclysm model, entity or Lionfish type is
 * imported; all native state is read through the existing reflective rig helpers.
 */
public final class EnderGuardianCompatClient {
    private static final String MOD_ID = "cataclysm";
    private static final String MODEL_CLASS =
            "com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model";
    /** Native pixels use Y down and the model ground plane is Y=24. */
    private static final float GROUND_Y = 24.0F;
    /** Uniform native scale: penguin bind 0..24 maps onto native -30..24. */
    private static final float ALIGN_SCALE = 2.25F;

    private static final String[] ROOT = {"root"};
    private static final String[] LOWER_BODY = {"root", "lowerbody"};
    private static final String[] UPPER_BODY = {"root", "lowerbody", "upperbody"};
    private static final String[] HELMET = {"root", "lowerbody", "upperbody", "helmet"};
    // Head and upperhelmet are validated for the pinned rest layout, but their own showModel
    // flags remain native phase state and must never gate the replacement.
    private static final String[] HEAD = {"root", "lowerbody", "upperbody", "helmet", "head"};
    private static final String[] UPPER_HELMET =
            {"root", "lowerbody", "upperbody", "helmet", "upperhelmet"};
    // Penguin historical positive-X wing follows the native anatomical left arm.
    private static final String[] LEFT_ARM = {"root", "lowerbody", "upperbody", "left_arm"};
    private static final String[] LEFT_ARM2 =
            {"root", "lowerbody", "upperbody", "left_arm", "left_arm2"};
    private static final String[] LEFT_FIST =
            {"root", "lowerbody", "upperbody", "left_arm", "left_arm2", "left_fist"};
    private static final String[] RIGHT_ARM = {"root", "lowerbody", "upperbody", "right_arm"};
    private static final String[] RIGHT_ARM2 =
            {"root", "lowerbody", "upperbody", "right_arm", "right_arm2"};
    private static final String[] RIGHT_FIST =
            {"root", "lowerbody", "upperbody", "right_arm", "right_arm2", "right_fist"};
    // Legs hang directly from the native root, matching the guardian model layout.
    private static final String[] LEFT_LEG = {"root", "left_leg"};
    private static final String[] RIGHT_LEG = {"root", "right_leg"};

    private EnderGuardianCompatClient() {
    }

    /**
     * Main renderer hook. The retained native pieces are submitted into the already-acquired native
     * buffer first; asking for a different RenderType afterwards lets a real BufferSource flush its
     * builder without invalidating the earlier consumer. Exactly one penguin body follows.
     */
    public static boolean renderNativePass(LivingEntity entity, EntityModel<?> model,
            PoseStack stack, VertexConsumer nativeBuffer, int light, int overlay, int color,
            MultiBufferSource buffers) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        PenguinSkinnedMesh skin = PenguinMeshModel.guardianSkin();
        if (bind == null || skin == null) {
            return false;
        }
        Matrix4f[] world = profile.world(bind);
        profile.retained.render(stack, nativeBuffer, light, overlay, color);
        VertexConsumer penguin = buffers.getBuffer(
                RenderType.entityCutoutNoCull(PenguinMeshModel.TEXTURE));
        skin.render(stack, penguin, light, overlay, color,
                PenguinRenderContext.fromNativeRig(world, new Matrix4f(), bind));
        return true;
    }

    /**
     * Glow-layer hook. Replays only the same retained helmet/fist terminals under the layer's own
     * eye material and overlay; no second penguin is drawn. The native {@code deathTime < 100}
     * condition stays outside this hook.
     */
    public static boolean renderGlowPass(LivingEntity entity, EntityModel<?> model,
            PoseStack stack, VertexConsumer buffer, int light, int overlay, int color) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        profile.retained.render(stack, buffer, light, overlay, color);
        return true;
    }

    /**
     * Visual-only gate, mirroring the accepted Ignis policy. Missing mod/model/profile/skin/clip or
     * a native invisible/glowing/baby/spectator draw returns null so the caller replays the complete
     * original render. Invalid paths, rest data or non-finite/tiny scales also fall back natively.
     */
    private static Profile replacementProfile(LivingEntity entity, EntityModel<?> model) {
        if (!ModList.get().isLoaded(MOD_ID)
                || model == null
                || !MODEL_CLASS.equals(model.getClass().getName())
                || !PenguinEntitySupport.isEnderGuardian(entity.getType())
                || !entity.hasEffect(ModEffects.PENGUIN)
                || entity.isBaby()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.isSpectator()
                || !PenguinMeshModel.ready()
                || PenguinMeshModel.guardianSkin() == null
                || PenguinMeshModel.animationClip() == null) {
            return null;
        }
        return resolve(model);
    }

    private static Profile resolve(EntityModel<?> model) {
        NativeAdvancedBoxRig.Path root = NativeAdvancedBoxRig.path(model, ROOT);
        NativeAdvancedBoxRig.Path lowerBody = NativeAdvancedBoxRig.path(model, LOWER_BODY);
        NativeAdvancedBoxRig.Path upperBody = NativeAdvancedBoxRig.path(model, UPPER_BODY);
        NativeAdvancedBoxRig.Path helmet = NativeAdvancedBoxRig.path(model, HELMET);
        NativeAdvancedBoxRig.Path head = NativeAdvancedBoxRig.path(model, HEAD);
        NativeAdvancedBoxRig.Path upperHelmet = NativeAdvancedBoxRig.path(model, UPPER_HELMET);
        NativeAdvancedBoxRig.Path leftArm = NativeAdvancedBoxRig.path(model, LEFT_ARM);
        NativeAdvancedBoxRig.Path leftArm2 = NativeAdvancedBoxRig.path(model, LEFT_ARM2);
        NativeAdvancedBoxRig.Path leftFist = NativeAdvancedBoxRig.path(model, LEFT_FIST);
        NativeAdvancedBoxRig.Path rightArm = NativeAdvancedBoxRig.path(model, RIGHT_ARM);
        NativeAdvancedBoxRig.Path rightArm2 = NativeAdvancedBoxRig.path(model, RIGHT_ARM2);
        NativeAdvancedBoxRig.Path rightFist = NativeAdvancedBoxRig.path(model, RIGHT_FIST);
        NativeAdvancedBoxRig.Path leftLeg = NativeAdvancedBoxRig.path(model, LEFT_LEG);
        NativeAdvancedBoxRig.Path rightLeg = NativeAdvancedBoxRig.path(model, RIGHT_LEG);
        if (root == null || lowerBody == null || upperBody == null || helmet == null
                || head == null || upperHelmet == null || leftArm == null || leftArm2 == null
                || leftFist == null || rightArm == null || rightArm2 == null || rightFist == null
                || leftLeg == null || rightLeg == null) {
            return null; // Different optional model layout: keep the complete native draw.
        }
        // Pinned 1.21.1-3.33 rest translations, with rot 0 and scales 1. A different model version
        // must keep its native renderer instead of receiving a wrong alignment or retained policy.
        if (!root.rest(0, 24, 0, 0, 0, 0)
                || !lowerBody.rest(0, -22, 0, 0, 0, 0)
                || !upperBody.rest(0, -7, 0, 0, 0, 0)
                || !helmet.rest(0, -28, -7, 0, 0, 0)
                || !head.rest(0, -4, -8, 0, 0, 0)
                || !upperHelmet.rest(0, 0, 0, 0, 0, 0)
                || !leftArm.rest(20, -25, 0, 0, 0, 0)
                || !leftArm2.rest(8.5F, 14, 0, 0, 0, 0)
                || !leftFist.rest(5.5F, 14, 0, 0, 0, 0)
                || !rightArm.rest(-20, -25, 0, 0, 0, 0)
                || !rightArm2.rest(-8.5F, 14, 0, 0, 0, 0)
                || !rightFist.rest(-5.5F, 14, 0, 0, 0, 0)
                || !leftLeg.rest(11, -22, 0, 0, 0, 0)
                || !rightLeg.rest(-11, -22, 0, 0, 0, 0)) {
            return null;
        }
        // Only the body chain and the limb transform chains are gated on native visibility/scale.
        // The helmet subtree (including head/upperhelmet) keeps its native phase-driven showModel.
        NativeAdvancedBoxRig.Path[] gated = {root, lowerBody, upperBody,
                leftArm, leftArm2, leftFist, rightArm, rightArm2, rightFist, leftLeg, rightLeg};
        for (NativeAdvancedBoxRig.Path path : gated) {
            if (!path.visible() || !path.usableScale()) {
                return null;
            }
        }
        NativeAdvancedRetainedGeometry retained = NativeAdvancedRetainedGeometry.resolve(model,
                HELMET, LEFT_FIST, RIGHT_FIST);
        if (retained == null) {
            return null;
        }
        NativeAdvancedBoxRig.Path[] jointPaths = new NativeAdvancedBoxRig.Path[PenguinRig.COUNT];
        jointPaths[PenguinRig.ROOT] = root;
        jointPaths[PenguinRig.PELVIS] = lowerBody;
        jointPaths[PenguinRig.CHEST] = upperBody;
        jointPaths[PenguinRig.HEAD] = helmet;
        jointPaths[4] = leftArm;
        jointPaths[5] = leftArm2;
        jointPaths[6] = leftFist;
        jointPaths[7] = rightArm;
        jointPaths[8] = rightArm2;
        jointPaths[9] = rightFist;
        jointPaths[10] = leftLeg;
        jointPaths[11] = leftLeg;
        jointPaths[12] = leftLeg;
        jointPaths[13] = rightLeg;
        jointPaths[14] = rightLeg;
        jointPaths[15] = rightLeg;
        return new Profile(jointPaths, retained);
    }

    /** Delta retargeting: D = F * inverse(B), then the ground-anchored A and immutable bind. */
    private static Matrix4f alignment() {
        return new Matrix4f()
                .translation(0.0F, GROUND_Y, 0.0F)
                .scale(ALIGN_SCALE)
                .translate(0.0F, -GROUND_Y, 0.0F);
    }

    private record Profile(NativeAdvancedBoxRig.Path[] jointPaths,
                           NativeAdvancedRetainedGeometry retained) {
        private Matrix4f[] world(PenguinAnimationClip bind) {
            Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
            for (int i = 0; i < world.length; i++) {
                Matrix4f delta = jointPaths[i].current().mul(jointPaths[i].initial().invert());
                // world = D * A * T(bind): D cancels the native rest pose, A grounds/scales it and
                // the immutable penguin bind supplies the per-vertex rest offset.
                world[i] = delta.mul(alignment()).translate(bind.bind(i));
            }
            // The native arms already bend and carry the giant fists. Keep the body-aligned wing
            // root origin, but take the native upper-arm orientation; the elbow and fist are
            // absolute native frames scaled uniformly. No outward offset or extra shoulder gap.
            world[4] = scaledOrientation(jointPaths[4].current(), world[4]);
            world[5] = jointPaths[5].current().scale(ALIGN_SCALE);
            world[6] = jointPaths[6].current().scale(ALIGN_SCALE);
            world[7] = scaledOrientation(jointPaths[7].current(), world[7]);
            world[8] = jointPaths[8].current().scale(ALIGN_SCALE);
            world[9] = jointPaths[9].current().scale(ALIGN_SCALE);
            return world;
        }

        private static Matrix4f scaledOrientation(Matrix4f current, Matrix4f base) {
            Matrix4f frame = new Matrix4f(current).scale(ALIGN_SCALE);
            frame.setTranslation(base.getTranslation(new Vector3f()));
            return frame;
        }
    }
}
