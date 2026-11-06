package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
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

/**
 * Optional visual-only Cataclysm Maledictus bridge.
 *
 * <p>No Cataclysm type is imported. The pinned model is identified by its exact class name, its
 * already-evaluated vanilla {@link ModelPart} hierarchy is read by name, and only the existing
 * Kuso penguin skin/bind pipeline is used. The boss action, AI, hitbox, grab sockets, weapons and
 * native head remain owned by the native renderer/entity.
 *
 * <p>Coordinate convention: {@link ModelPart} positions and the penguin bind are in model pixels
 * with native Y pointing down, ground at Y=24. The per-joint world matrices are in that same native
 * pixel space. The penguin skin's own {@code /16} conversion stays in
 * {@link PenguinSkinnedMesh#render}. The matrix/path/retained-terminal mechanics now live in
 * {@link NativeModelPartRig} and {@link NativeRetainedGeometry}; this class only owns the
 * Maledictus-specific paths, alignment and retained list.
 */
public final class MaledictusCompatClient {
    private static final String MOD_ID = "cataclysm";
    private static final String MODEL_CLASS =
            "com.github.L_Ender.cataclysm.client.model.entity.Maledictus_Model";
    private static final float GROUND_Y = 24.0F;
    /** Native head/neck pivot derived from roots 24 - 24 + pelvis 2 + body 1.6 - head 16.6. */
    private static final float NECK_Y = -13.0F;
    /** Native head/neck Z from roots 0 + berserker -3 + body 3.6 - head 4. */
    private static final float NECK_Z = -3.4F;
    private static final float BIND_BODY_HEIGHT = 24.0F;
    /** Maps the 24-pixel penguin bind body onto the native ground-to-neck span. */
    private static final float ALIGN_SCALE = (GROUND_Y - NECK_Y) / BIND_BODY_HEIGHT;

    private static final String[] ROOT = {"roots", "berserker"};
    private static final String[] PELVIS = {"roots", "berserker", "pelvis"};
    private static final String[] BODY = {"roots", "berserker", "pelvis", "body"};
    private static final String[] HEAD = {"roots", "berserker", "pelvis", "body", "head"};
    private static final String[] RIGHT_WING =
            {"roots", "berserker", "pelvis", "body", "left_shoulder"};
    private static final String[] RIGHT_WING_MID =
            {"roots", "berserker", "pelvis", "body", "left_shoulder", "left_arm"};
    private static final String[] RIGHT_WING_TIP =
            {"roots", "berserker", "pelvis", "body", "left_shoulder", "left_arm",
                    "left_front_arm"};
    private static final String[] LEFT_WING =
            {"roots", "berserker", "pelvis", "body", "right_shoulder"};
    private static final String[] LEFT_WING_MID =
            {"roots", "berserker", "pelvis", "body", "right_shoulder", "right_arm"};
    private static final String[] LEFT_WING_TIP =
            {"roots", "berserker", "pelvis", "body", "right_shoulder", "right_arm",
                    "right_front_arm"};
    private static final String[] RIGHT_LEG =
            {"roots", "berserker", "legs", "left_leg"};
    private static final String[] RIGHT_KNEE =
            {"roots", "berserker", "legs", "left_leg", "left_front_leg"};
    private static final String[] LEFT_LEG =
            {"roots", "berserker", "legs", "right_leg"};
    private static final String[] LEFT_KNEE =
            {"roots", "berserker", "legs", "right_leg", "right_front_leg"};
    private static final String[] RIGHT_MACE = NativeModelPartRig.append(LEFT_WING_TIP, "right_mace");
    private static final String[] LEFT_MACE = NativeModelPartRig.append(RIGHT_WING_TIP, "left_mace");
    private static final String[] BOW = NativeModelPartRig.append(LEFT_WING_TIP, "bow");
    private static final String[] HALBERD = NativeModelPartRig.append(LEFT_WING_TIP, "halberd");
    private static final String[] SPIRIT_LEFT_WING = NativeModelPartRig.append(BODY, "left_wing");
    private static final String[] SPIRIT_RIGHT_WING = NativeModelPartRig.append(BODY, "right_wing");

    private MaledictusCompatClient() {
    }

    /**
     * Main-pass hook entry point. Returns true only when the complete penguin body and the retained
     * native geometry were drawn and the caller must skip the native whole-model render.
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
        Matrix4f[] world = profile.rig().world(bind);
        PenguinAnimationPose pose = PenguinRenderContext.fromNativeRig(
                world, new Matrix4f(), bind);
        // The native consumer was acquired by LivingEntityRenderer before this hook. Finish it
        // BEFORE requesting a different render type: BufferSource may flush/reuse its builder,
        // making a retained reference to the previous native consumer invalid ("Not building!").
        profile.retained().render(poseStack, nativeBuffer, packedLight, packedOverlay, color);
        // Main pass owns the one and only penguin body draw. The ghost layer below only draws the
        // retained native head/weapons/spirit-wing geometry with its native ghost material.
        VertexConsumer penguinBuffer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(PenguinMeshModel.TEXTURE));
        skin.render(poseStack, penguinBuffer, packedLight, packedOverlay, color, pose);
        return true;
    }

    /**
     * Ghost-layer hook entry point. The caller keeps the native ghost {@link VertexConsumer},
     * packed light/overlay and hurt/death color. Only the retained native geometry is replayed;
     * the original body geometry and any second penguin body are intentionally absent.
     */
    public static boolean renderGhostPass(LivingEntity entity, EntityModel<?> model,
                                          PoseStack poseStack, VertexConsumer ghostBuffer,
                                          int packedLight, int packedOverlay, int color) {
        Profile profile = replacementProfile(entity, model);
        if (profile == null) {
            return false;
        }
        profile.retained().render(poseStack, ghostBuffer, packedLight, packedOverlay, color);
        return true;
    }

    /**
     * Visual-only gate shared by the main and ghost passes. It deliberately does not use
     * {@code PenguinRenderSupport.shouldReplace}: the boss must never enter Kuso action/death/
     * dance state, while its native invisible/glowing/baby/spectator draws stay completely native.
     */
    private static Profile replacementProfile(LivingEntity entity, EntityModel<?> model) {
        if (!ModList.get().isLoaded(MOD_ID)
                || model == null
                || !MODEL_CLASS.equals(model.getClass().getName())
                || !PenguinEntitySupport.isMaledictus(entity.getType())
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
        return resolve(model);
    }

    private static Profile resolve(EntityModel<?> model) {
        if (!(model instanceof HierarchicalModel<?> hierarchical)) {
            return null;
        }
        ModelPart root = hierarchical.root();
        ModelPart roots = NativeModelPartRig.child(root, "roots");
        ModelPart berserker = NativeModelPartRig.child(roots, "berserker");
        ModelPart pelvis = NativeModelPartRig.child(berserker, "pelvis");
        ModelPart body = NativeModelPartRig.child(pelvis, "body");
        ModelPart head = NativeModelPartRig.child(body, "head");
        if (roots == null || berserker == null || pelvis == null || body == null
                || head == null) {
            return null;
        }
        // Pinned native bind anchors. A different model version must keep its complete native
        // renderer instead of receiving the wrong penguin alignment.
        if (!NativeModelPartRig.rest(roots, 0.0F, 24.0F, 0.0F)
                || !NativeModelPartRig.rest(berserker, 0.0F, -24.0F, -3.0F)
                || !NativeModelPartRig.rest(pelvis, 0.0F, 2.0F, 0.0F)
                || !NativeModelPartRig.rest(body, 0.0F, 1.6F, 3.6F)
                || !NativeModelPartRig.rest(head, 0.0F, -16.6F, -4.0F)) {
            return null;
        }

        String[][] jointNames = new String[PenguinRig.COUNT][];
        jointNames[PenguinRig.ROOT] = ROOT;
        jointNames[PenguinRig.PELVIS] = PELVIS;
        jointNames[PenguinRig.CHEST] = BODY;
        jointNames[PenguinRig.HEAD] = HEAD;
        // Historical penguin names use the opposite X side from the native anatomical names:
        // positive-X penguin right_wing (root 4) follows native LEFT shoulder/arm/front_arm.
        jointNames[4] = RIGHT_WING;
        jointNames[5] = RIGHT_WING_MID;
        jointNames[6] = RIGHT_WING_TIP;
        jointNames[7] = LEFT_WING;
        jointNames[8] = LEFT_WING_MID;
        jointNames[9] = LEFT_WING_TIP;
        // Same X-side convention for the legs. The native front_leg is the knee/foot chain;
        // the pinned model has no separate foot bone, so knee and foot share that delta.
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
        NativeRetainedGeometry retained = NativeRetainedGeometry.resolve(root,
                HEAD, RIGHT_MACE, LEFT_MACE, BOW, HALBERD, SPIRIT_LEFT_WING,
                SPIRIT_RIGHT_WING);
        return retained == null ? null : new Profile(rig, retained);
    }

    private static Matrix4f alignment() {
        // Y is scaled around the native ground plane. The final Z translation puts penguin
        // bind Z=0 on the retained native neck/body centreline; it is not a hand offset.
        return new Matrix4f()
                .translation(0.0F, GROUND_Y, NECK_Z)
                .scale(ALIGN_SCALE, ALIGN_SCALE, ALIGN_SCALE)
                .translate(0.0F, -GROUND_Y, 0.0F);
    }

    private record Profile(NativeModelPartRig rig, NativeRetainedGeometry retained) {
    }
}
