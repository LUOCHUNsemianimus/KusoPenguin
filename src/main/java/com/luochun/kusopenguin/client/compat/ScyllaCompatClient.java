package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.BossDancePose;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinAnimationPose;
import com.luochun.kusopenguin.client.PenguinMeshModel;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRig;
import com.luochun.kusopenguin.client.PenguinSkinnedMesh;
import com.luochun.kusopenguin.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Optional visual-only Cataclysm 1.21.1-3.33 Scylla bridge.
 *
 * <p>Scylla owns a custom renderer that calls the concrete model directly instead of
 * {@code LivingEntityRenderer.render}. This class is therefore entered from three exact Scylla
 * hooks: the main renderer call, the snake ghost layer and the generic glowing layer only when the
 * exact Scylla model/entity is present. No Cataclysm type is imported and no native
 * AI/animation/attack/anchor/socket is changed.
 *
 * <p>Native model pixels use Y down with the ground at Y=24. The evaluated pose is sampled fresh
 * from the already-animated {@link ModelPart} fields on every draw; only immutable paths and
 * retained-terminal metadata are kept for the lifetime of a resolved profile.
 */
public final class ScyllaCompatClient {
    private static final String MOD_ID = "cataclysm";
    private static final String MODEL_CLASS =
            "com.github.L_Ender.cataclysm.client.model.entity.Scylla_Model";
    private static final float GROUND_Y = 24.0F;
    /**
     * World Y of the native head pivot: everything 24 + body -20 + chest -5 + head -6.5.
     * The renderer's 1.15 scale is applied outside this matrix and must not be multiplied again.
     */
    private static final float HEAD_PIVOT_Y = -7.5F;
    private static final float HEAD_PIVOT_Z = 0.0F;
    private static final float BIND_BODY_HEIGHT = 24.0F;
    // Native SPAWN_IDLE hides everything at zero scale; SPAWN crosses tiny scales in 1 ms.
    // Keep that native draw until triangles have meaningful area for the continuous skin.
    private static final float MIN_BODY_SCALE = 0.01F;
    /** First alignment: ground-to-head 31.5 pixels over the 24-pixel penguin bind. */
    private static final float ALIGN_SCALE = (GROUND_Y - HEAD_PIVOT_Y) / BIND_BODY_HEIGHT;

    private static final String[] ROOT = {"everything", "scylla"};
    private static final String[] PELVIS = {"everything", "scylla", "body"};
    private static final String[] CHEST = {"everything", "scylla", "body", "chest"};
    private static final String[] HEAD =
            {"everything", "scylla", "body", "chest", "head"};
    // Historical penguin side names are opposite to the native anatomical names:
    // positive-X right_wing follows native l_arm/l_arm2, negative-X left follows r_arm/r_arm2.
    private static final String[] RIGHT_WING =
            {"everything", "scylla", "body", "chest", "l_arm"};
    private static final String[] RIGHT_WING_MID =
            {"everything", "scylla", "body", "chest", "l_arm", "l_arm2"};
    private static final String[] LEFT_WING =
            {"everything", "scylla", "body", "chest", "r_arm"};
    private static final String[] LEFT_WING_MID =
            {"everything", "scylla", "body", "chest", "r_arm", "r_arm2"};
    // Same X-side convention for the legs: penguin right (positive X) follows native l_leg.
    private static final String[] RIGHT_LEG = {"everything", "scylla", "l_leg"};
    private static final String[] RIGHT_KNEE = {"everything", "scylla", "l_leg", "l_leg2"};
    private static final String[] LEFT_LEG = {"everything", "scylla", "r_leg"};
    private static final String[] LEFT_KNEE = {"everything", "scylla", "r_leg", "r_leg2"};

    private static final String[] ANCHOR =
            {"everything", "scylla", "body", "chest", "l_arm", "l_arm2", "anchor"};
    private static final String[] CHAIN =
            {"everything", "scylla", "body", "chest", "l_arm", "l_arm2", "chain_main"};
    private static final String[] SLEEP_ANCHOR = {"anchor2"};
    private static final String[] BELT_SNAKE =
            {"everything", "scylla", "body", "belt_sea_snake_mody", "belt_sea_snake"};
    private static final String[] LEFT_SHOULDER_SNAKE =
            {"everything", "scylla", "body", "chest", "l_arm", "l_armor", "l_sea_snake"};
    private static final String[] RIGHT_SHOULDER_SNAKE =
            {"everything", "scylla", "body", "chest", "r_arm", "r_sea_snake"};

    /** Main pass: head + live anchor/chain + sleep anchor + snake ornaments. */
    private static final String[][] MAIN_RETAINED = {
            HEAD, ANCHOR, CHAIN, SLEEP_ANCHOR, BELT_SNAKE, LEFT_SHOULDER_SNAKE,
            RIGHT_SHOULDER_SNAKE
    };
    /** Snake ghost pass: only the three snake ornament branches, under the snake texture. */
    private static final String[][] SNAKE_RETAINED = {
            BELT_SNAKE, LEFT_SHOULDER_SNAKE, RIGHT_SHOULDER_SNAKE
    };
    /** Eye glow pass: original head subtree (eyes and hair live under it). */
    private static final String[][] EYE_RETAINED = {HEAD};

    private ScyllaCompatClient() {
    }

    /**
     * Main renderer hook. Replays retained native pieces into the already-acquired native buffer
     * first, then requests the penguin material and draws exactly one penguin body.
     */
    public static boolean renderNativePass(LivingEntity entity, EntityModel<?> model,
                                           PoseStack poseStack, VertexConsumer nativeBuffer,
                                           int packedLight, int packedOverlay, int color,
                                           MultiBufferSource bufferSource) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        PenguinSkinnedMesh skin = PenguinMeshModel.currentSkin();
        if (bind == null || skin == null) {
            return false;
        }
        Matrix4f[] nativeWorld = profile.world(bind);
        BossDancePose.Sample dance = BossDancePose.sample(
                entity, nativeWorld[PenguinRig.PELVIS], bind);
        Matrix4f[] world = dance == null ? nativeWorld : dance.placed();
        PenguinAnimationPose pose = PenguinRenderContext.fromNativeRig(
                world, new Matrix4f(), bind);
        // Finish the native consumer before requesting a different RenderType; a real
        // BufferSource may flush/reuse its builder and invalidate an earlier consumer.
        profile.main().render(poseStack, nativeBuffer, packedLight, packedOverlay, color);
        VertexConsumer penguinBuffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(PenguinMeshModel.TEXTURE));
        skin.render(poseStack, penguinBuffer, packedLight, packedOverlay, color, pose);
        return true;
    }

    /**
     * Snake-layer hook. Only snake ornament terminals are replayed under the original ghost
     * texture/alpha/color; the native body and any second penguin body are omitted.
     */
    public static boolean renderSnakePass(LivingEntity entity, EntityModel<?> model,
                                          PoseStack poseStack, VertexConsumer snakeBuffer,
                                          int packedLight, int packedOverlay, int color) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        profile.snake().render(poseStack, snakeBuffer, packedLight, packedOverlay, color);
        return true;
    }

    /**
     * Generic glowing-layer hook, gated to the exact Scylla model/entity. Only the original head
     * subtree is replayed under the layer's own eye texture; every other user of the layer keeps
     * the complete native model.
     */
    public static boolean renderEyeGlowPass(LivingEntity entity, EntityModel<?> model,
                                            PoseStack poseStack, VertexConsumer eyeBuffer,
                                            int packedLight, int packedOverlay, int color) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        profile.eye().render(poseStack, eyeBuffer, packedLight, packedOverlay, color);
        return true;
    }

    /**
     * Visual-only gate. Missing mod/model/profile/skin/clip or a native invisible/glowing/baby/
     * spectator draw returns null so the caller replays the complete original call. Shield-like
     * second model instances are distinct objects and are never assumed to share this profile.
     */
    private static Profile replacementProfile(LivingEntity entity, EntityModel<?> model) {
        if (!ModList.get().isLoaded(MOD_ID)
                || model == null
                || !MODEL_CLASS.equals(model.getClass().getName())
                || !PenguinEntitySupport.isScylla(entity.getType())
                || !entity.hasEffect(ModEffects.PENGUIN)
                || entity.isBaby()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.isSpectator()
                || !PenguinMeshModel.ready()
                || PenguinMeshModel.currentSkin() == null
                || PenguinMeshModel.animationClip() == null) {
            return null;
        }
        Profile profile = resolve(model);
        if (profile == null) {
            return null;
        }
        NativeModelPartRig.JointPath bodyRoot = NativeModelPartRig.path(
                ((HierarchicalModel<?>) model).root(), ROOT);
        for (ModelPart part : bodyRoot.chain()) {
            if (!part.visible || Math.abs(part.xScale) < MIN_BODY_SCALE
                    || Math.abs(part.yScale) < MIN_BODY_SCALE
                    || Math.abs(part.zScale) < MIN_BODY_SCALE) {
                // The separate root/anchor2 remains native, including its sleep visibility.
                return null;
            }
        }
        return profile;
    }

    private static Profile resolve(EntityModel<?> model) {
        if (!(model instanceof HierarchicalModel<?> hierarchical)) {
            return null;
        }
        ModelPart root = hierarchical.root();
        NativeModelPartRig.JointPath everything =
                NativeModelPartRig.path(root, "everything");
        NativeModelPartRig.JointPath scylla = NativeModelPartRig.path(root, "everything",
                "scylla");
        NativeModelPartRig.JointPath body = NativeModelPartRig.path(root, "everything",
                "scylla", "body");
        NativeModelPartRig.JointPath chest = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest");
        NativeModelPartRig.JointPath head = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "head");
        NativeModelPartRig.JointPath lArm = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "l_arm");
        NativeModelPartRig.JointPath lArm2 = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "l_arm", "l_arm2");
        NativeModelPartRig.JointPath rArm = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "r_arm");
        NativeModelPartRig.JointPath rArm2 = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "r_arm", "r_arm2");
        NativeModelPartRig.JointPath lLeg = NativeModelPartRig.path(root, "everything",
                "scylla", "l_leg");
        NativeModelPartRig.JointPath lLeg2 = NativeModelPartRig.path(root, "everything",
                "scylla", "l_leg", "l_leg2");
        NativeModelPartRig.JointPath rLeg = NativeModelPartRig.path(root, "everything",
                "scylla", "r_leg");
        NativeModelPartRig.JointPath rLeg2 = NativeModelPartRig.path(root, "everything",
                "scylla", "r_leg", "r_leg2");
        NativeModelPartRig.JointPath anchor = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "l_arm", "l_arm2", "anchor");
        NativeModelPartRig.JointPath chain = NativeModelPartRig.path(root, "everything",
                "scylla", "body", "chest", "l_arm", "l_arm2", "chain_main");
        NativeModelPartRig.JointPath sleepAnchor = NativeModelPartRig.path(root, "anchor2");
        if (everything == null || scylla == null || body == null || chest == null
                || head == null || lArm == null || lArm2 == null || rArm == null
                || rArm2 == null || lLeg == null || lLeg2 == null || rLeg == null
                || rLeg2 == null || anchor == null || chain == null || sleepAnchor == null) {
            return null;
        }
        // Pinned 1.21.1-3.33 rest translations. A different model version must keep its complete
        // native renderer instead of receiving a wrong alignment or retained policy.
        if (!NativeModelPartRig.rest(everything.terminal(), 0.0F, 24.0F, 0.0F)
                || !NativeModelPartRig.rest(scylla.terminal(), 0.0F, 0.0F, 0.0F)
                || !NativeModelPartRig.rest(body.terminal(), 0.0F, -20.0F, 0.0F)
                || !NativeModelPartRig.rest(chest.terminal(), 0.0F, -5.0F, 0.0F)
                || !NativeModelPartRig.rest(head.terminal(), 0.0F, -6.5F, 0.0F)
                || !NativeModelPartRig.rest(lArm.terminal(), 5.5F, -4.0F, 0.0F)
                || !NativeModelPartRig.rest(lArm2.terminal(), 0.0F, 5.0F, 0.0F)
                || !NativeModelPartRig.rest(rArm.terminal(), -5.5F, -4.0F, 0.0F)
                || !NativeModelPartRig.rest(rArm2.terminal(), 0.0F, 5.0F, 0.0F)
                || !NativeModelPartRig.rest(lLeg.terminal(), 2.0F, -20.0F, 0.0F)
                || !NativeModelPartRig.rest(lLeg2.terminal(), 0.0F, 9.0F, 0.0F)
                || !NativeModelPartRig.rest(rLeg.terminal(), -2.0F, -20.0F, 0.0F)
                || !NativeModelPartRig.rest(rLeg2.terminal(), 0.0F, 9.0F, 0.0F)
                || !NativeModelPartRig.rest(anchor.terminal(), -1.5F, 8.0F, 0.0F)
                || !NativeModelPartRig.rest(chain.terminal(), 0.0F, 8.0F, 0.0F)
                || !NativeModelPartRig.rest(sleepAnchor.terminal(), 4.0F, 8.0F, 0.0F)) {
            return null;
        }

        String[][] jointNames = new String[PenguinRig.COUNT][];
        jointNames[PenguinRig.ROOT] = ROOT;
        jointNames[PenguinRig.PELVIS] = PELVIS;
        jointNames[PenguinRig.CHEST] = CHEST;
        jointNames[PenguinRig.HEAD] = HEAD;
        jointNames[4] = RIGHT_WING;
        jointNames[5] = RIGHT_WING_MID;
        jointNames[6] = RIGHT_WING_MID;
        jointNames[7] = LEFT_WING;
        jointNames[8] = LEFT_WING_MID;
        jointNames[9] = LEFT_WING_MID;
        jointNames[10] = RIGHT_LEG;
        jointNames[11] = RIGHT_KNEE;
        jointNames[12] = RIGHT_KNEE;
        jointNames[13] = LEFT_LEG;
        jointNames[14] = LEFT_KNEE;
        jointNames[15] = LEFT_KNEE;
        NativeModelPartRig rig = NativeModelPartRig.create(root, jointNames, alignment());
        if (rig == null) {
            return null;
        }
        NativeRetainedGeometry main = NativeRetainedGeometry.resolve(root, MAIN_RETAINED);
        NativeRetainedGeometry snake = NativeRetainedGeometry.resolve(root, SNAKE_RETAINED);
        NativeRetainedGeometry eye = NativeRetainedGeometry.resolve(root, EYE_RETAINED);
        if (main == null || snake == null || eye == null) {
            return null;
        }
        return new Profile(rig, main, snake, eye, lArm, lArm2, anchor, rArm, rArm2, chest);
    }

    private static Matrix4f alignment() {
        // Y scale is anchored at the native ground plane; Z stays 0 because the Scylla head
        // centreline is Z=0. The renderer's native 1.15 scale is already outside this matrix.
        return new Matrix4f()
                .translation(0.0F, GROUND_Y, HEAD_PIVOT_Z)
                .scale(ALIGN_SCALE, ALIGN_SCALE, ALIGN_SCALE)
                .translate(0.0F, -GROUND_Y, 0.0F);
    }

    private record Profile(NativeModelPartRig rig, NativeRetainedGeometry main,
                           NativeRetainedGeometry snake, NativeRetainedGeometry eye,
                           NativeModelPartRig.JointPath leftArm,
                           NativeModelPartRig.JointPath leftForearm,
                           NativeModelPartRig.JointPath anchor,
                           NativeModelPartRig.JointPath rightArm,
                           NativeModelPartRig.JointPath rightForearm,
                           NativeModelPartRig.JointPath chest) {
        private Matrix4f[] world(PenguinAnimationClip bind) {
            Matrix4f[] frames = rig.world(bind);
            // Scylla's bind arms already bend forward to hold the anchor. Delta-only retargeting
            // cancels that bend and leaves a straight penguin wing below the native weapon.
            // Keep the body-aligned shoulder socket, but use the native bent elbow/grip frames.
            frames[4] = leftArm.current().scale(ALIGN_SCALE)
                    .setTranslation(frames[4].getTranslation(new Vector3f()));
            frames[5] = leftForearm.current().scale(ALIGN_SCALE);
            ModelPart grip = anchor.terminal();
            frames[6] = leftForearm.current().translate(grip.x, grip.y, grip.z)
                    .scale(ALIGN_SCALE);
            frames[7] = rightArm.current().scale(ALIGN_SCALE)
                    .setTranslation(frames[7].getTranslation(new Vector3f()));
            frames[8] = rightForearm.current().scale(ALIGN_SCALE);
            frames[9] = rightForearm.current().translate(0.0F, 8.0F, 0.0F)
                    .scale(ALIGN_SCALE);
            // The free anatomical right arm was inside the wider penguin belly. Move only its
            // forearm/tip along the chest axis. Keep the shoulder root body-anchored so the
            // upper wing bridges into the belly instead of detaching; native sockets stay put.
            Vector3f outward = chest.current().transformDirection(
                    new Vector3f(-3.5F * ALIGN_SCALE, 0.0F, 0.0F));
            for (int i = 8; i <= 9; i++) {
                frames[i].translateLocal(outward.x, outward.y, outward.z);
            }
            // Anchor visibility/scale belongs to the weapon mesh, not to a disappearing wrist.
            // No native part, global clip bind, or Maledictus frame is changed here.
            return frames;
        }
    }
}
