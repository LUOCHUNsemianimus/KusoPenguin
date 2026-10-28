package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinAnimationPose;
import com.luochun.kusopenguin.client.PenguinMeshModel;
import com.luochun.kusopenguin.client.PenguinMeshPreview;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRig;
import com.luochun.kusopenguin.client.PenguinSkinnedMesh;
import com.luochun.kusopenguin.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
 * {@link PenguinSkinnedMesh#render}.
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
    private static final float REST_EPSILON = 1.0E-4F;

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
        NativeRig rig = replacementRig(entity, model);
        if (rig == null) {
            return false;
        }
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        PenguinSkinnedMesh skin = PenguinMeshModel.currentSkin();
        if (bind == null || skin == null) {
            return false;
        }
        Matrix4f[] world = rig.world(bind);
        PenguinAnimationPose pose = PenguinRenderContext.fromNativeRig(
                world, new Matrix4f(), bind);
        // The native consumer was acquired by LivingEntityRenderer before this hook. Finish it
        // BEFORE requesting a different render type: BufferSource may flush/reuse its builder,
        // making a retained reference to the previous native consumer invalid ("Not building!").
        rig.renderRetained(poseStack, nativeBuffer, packedLight, packedOverlay, color);
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
        NativeRig rig = replacementRig(entity, model);
        if (rig == null) {
            return false;
        }
        rig.renderRetained(poseStack, ghostBuffer, packedLight, packedOverlay, color);
        return true;
    }

    /**
     * Visual-only gate shared by the main and ghost passes. It deliberately does not use
     * {@code PenguinRenderSupport.shouldReplace}: the boss must never enter Kuso action/death/
     * dance state, while its native invisible/glowing/baby/spectator draws stay completely native.
     */
    private static NativeRig replacementRig(LivingEntity entity, EntityModel<?> model) {
        if (!ModList.get().isLoaded(MOD_ID)
                || model == null
                || !MODEL_CLASS.equals(model.getClass().getName())
                || !PenguinEntitySupport.isNativeAnimatedBoss(entity.getType())
                || !entity.hasEffect(ModEffects.PENGUIN)
                || entity.isBaby()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.isSpectator()
                || !PenguinMeshPreview.isEnabled()
                || PenguinMeshModel.currentSkin() == null
                || PenguinMeshModel.animationClip() == null) {
            return null;
        }
        return NativeRig.resolve(model);
    }

    /**
     * Immutable per-draw path metadata. ModelPart transforms are only read; no native part field,
     * visibility flag or pose is mutated and no state survives the draw.
     */
    private record NativeRig(JointPath[] joints, List<JointPath> retained) {
        private static final String[] ROOT = {"roots", "berserker"};
        private static final String[] PELVIS = {"roots", "berserker", "pelvis"};
        private static final String[] BODY = {"roots", "berserker", "pelvis", "body"};
        private static final String[] HEAD =
                {"roots", "berserker", "pelvis", "body", "head"};
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
        private static final String[] RIGHT_MACE = append(LEFT_WING_TIP, "right_mace");
        private static final String[] LEFT_MACE = append(RIGHT_WING_TIP, "left_mace");
        private static final String[] BOW = append(LEFT_WING_TIP, "bow");
        private static final String[] HALBERD = append(LEFT_WING_TIP, "halberd");
        private static final String[] SPIRIT_LEFT_WING = append(BODY, "left_wing");
        private static final String[] SPIRIT_RIGHT_WING = append(BODY, "right_wing");

        private static NativeRig resolve(EntityModel<?> model) {
            if (!(model instanceof HierarchicalModel<?> hierarchical)) {
                return null;
            }
            ModelPart root = hierarchical.root();
            ModelPart roots = child(root, "roots");
            ModelPart berserker = child(roots, "berserker");
            ModelPart pelvis = child(berserker, "pelvis");
            ModelPart body = child(pelvis, "body");
            ModelPart head = child(body, "head");
            if (roots == null || berserker == null || pelvis == null || body == null
                    || head == null) {
                return null;
            }
            // Pinned native bind anchors. A different model version must keep its complete native
            // renderer instead of receiving the wrong penguin alignment.
            if (!rest(roots, 0.0F, 24.0F, 0.0F)
                    || !rest(berserker, 0.0F, -24.0F, -3.0F)
                    || !rest(pelvis, 0.0F, 2.0F, 0.0F)
                    || !rest(body, 0.0F, 1.6F, 3.6F)
                    || !rest(head, 0.0F, -16.6F, -4.0F)) {
                return null;
            }

            JointPath[] joints = new JointPath[PenguinRig.COUNT];
            joints[PenguinRig.ROOT] = path(root, ROOT);
            joints[PenguinRig.PELVIS] = path(root, PELVIS);
            joints[PenguinRig.CHEST] = path(root, BODY);
            joints[PenguinRig.HEAD] = path(root, HEAD);
            // Historical penguin names use the opposite X side from the native anatomical names:
            // positive-X penguin right_wing (root 4) follows native LEFT shoulder/arm/front_arm.
            joints[4] = path(root, RIGHT_WING);
            joints[5] = path(root, RIGHT_WING_MID);
            joints[6] = path(root, RIGHT_WING_TIP);
            joints[7] = path(root, LEFT_WING);
            joints[8] = path(root, LEFT_WING_MID);
            joints[9] = path(root, LEFT_WING_TIP);
            // Same X-side convention for the legs. The native front_leg is the knee/foot chain;
            // the pinned model has no separate foot bone, so knee and foot share that delta.
            joints[10] = path(root, RIGHT_LEG);
            joints[11] = path(root, RIGHT_KNEE);
            joints[12] = joints[11];
            joints[13] = path(root, LEFT_LEG);
            joints[14] = path(root, LEFT_KNEE);
            joints[15] = joints[14];
            for (JointPath joint : joints) {
                if (joint == null) {
                    return null;
                }
            }

            JointPath headPath = path(root, HEAD);
            JointPath weaponRight = path(root, RIGHT_MACE);
            JointPath weaponLeft = path(root, LEFT_MACE);
            JointPath bow = path(root, BOW);
            JointPath halberd = path(root, HALBERD);
            JointPath spiritLeft = path(root, SPIRIT_LEFT_WING);
            JointPath spiritRight = path(root, SPIRIT_RIGHT_WING);
            if (headPath == null || weaponRight == null || weaponLeft == null || bow == null
                    || halberd == null || spiritLeft == null || spiritRight == null) {
                return null;
            }
            List<JointPath> retained = new ArrayList<>(7);
            retained.add(headPath);
            retained.add(weaponRight);
            retained.add(weaponLeft);
            retained.add(bow);
            retained.add(halberd);
            retained.add(spiritLeft);
            retained.add(spiritRight);
            return new NativeRig(joints, List.copyOf(retained));
        }

        private Matrix4f[] world(PenguinAnimationClip bind) {
            Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
            for (int i = 0; i < world.length; i++) {
                Matrix4f delta = new Matrix4f(joints[i].current())
                        .mul(new Matrix4f(joints[i].initial()).invert());
                // Bind cancellation belongs to fromNativeRig: world = D * A * T(bind), therefore
                // the fed pose's skin matrix is exactly D * A in native model pixels.
                world[i] = delta.mul(alignment()).translate(bind.bind(i));
            }
            return world;
        }

        /**
         * Replays the retained native head+horn, currently visible weapons and spiritual back
         * wings. The full ancestor chain is walked with {@code translateAndRotate}, and every
         * ancestor's own visibility is respected. Rendering a terminal ModelPart keeps its native
         * children, so horns, bow strings, halberd blades and wing2 all stay attached.
         */
        private void renderRetained(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                    int packedOverlay, int color) {
            for (JointPath path : retained) {
                ModelPart[] chain = path.chain();
                boolean visible = true;
                for (ModelPart part : chain) {
                    if (!part.visible) {
                        visible = false;
                        break;
                    }
                }
                if (!visible) {
                    continue;
                }
                poseStack.pushPose();
                try {
                    for (int i = 0; i < chain.length - 1; i++) {
                        chain[i].translateAndRotate(poseStack);
                    }
                    chain[chain.length - 1].render(poseStack, buffer, packedLight, packedOverlay,
                            color);
                } finally {
                    poseStack.popPose();
                }
            }
        }

        private static Matrix4f alignment() {
            // Y is scaled around the native ground plane. The final Z translation puts penguin
            // bind Z=0 on the retained native neck/body centreline; it is not a hand offset.
            return new Matrix4f()
                    .translation(0.0F, GROUND_Y, NECK_Z)
                    .scale(ALIGN_SCALE, ALIGN_SCALE, ALIGN_SCALE)
                    .translate(0.0F, -GROUND_Y, 0.0F);
        }

        private static JointPath path(ModelPart root, String[] names) {
            List<ModelPart> chain = new ArrayList<>(names.length + 1);
            chain.add(root);
            ModelPart current = root;
            for (String name : names) {
                current = child(current, name);
                if (current == null) {
                    return null;
                }
                chain.add(current);
            }
            return new JointPath(chain.toArray(ModelPart[]::new));
        }

        private static ModelPart child(ModelPart parent, String name) {
            return parent != null && parent.hasChild(name) ? parent.getChild(name) : null;
        }

        private static boolean rest(ModelPart part, float x, float y, float z) {
            PartPose pose = part.getInitialPose();
            return Math.abs(pose.x - x) <= REST_EPSILON
                    && Math.abs(pose.y - y) <= REST_EPSILON
                    && Math.abs(pose.z - z) <= REST_EPSILON;
        }

        private static String[] append(String[] path, String child) {
            String[] result = new String[path.length + 1];
            System.arraycopy(path, 0, result, 0, path.length);
            result[path.length] = child;
            return result;
        }
    }

    /**
     * Full root-to-terminal ancestor chain. Rest uses {@link ModelPart#getInitialPose()}; animated
     * state uses the current translation, rotation and scale. Both are full pixel-space products,
     * so {@code F * inverse(B)} includes every ancestor, not just a local Euler copy.
     */
    private record JointPath(ModelPart[] chain) {
        private Matrix4f current() {
            return chain(false);
        }

        private Matrix4f initial() {
            return chain(true);
        }

        private Matrix4f chain(boolean initial) {
            Matrix4f result = new Matrix4f();
            for (ModelPart part : chain) {
                result.mul(local(part, initial));
            }
            return result;
        }

        private static Matrix4f local(ModelPart part, boolean initial) {
            if (initial) {
                PartPose pose = part.getInitialPose();
                return new Matrix4f()
                        .translation(pose.x, pose.y, pose.z)
                        .rotateZYX(pose.zRot, pose.yRot, pose.xRot);
            }
            return new Matrix4f()
                    .translation(part.x, part.y, part.z)
                    .rotateZYX(part.zRot, part.yRot, part.xRot)
                    .scale(part.xScale, part.yScale, part.zScale);
        }
    }
}
