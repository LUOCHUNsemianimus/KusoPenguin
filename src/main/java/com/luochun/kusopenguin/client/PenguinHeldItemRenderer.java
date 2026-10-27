package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The single third-person held-item wing mount used by every penguin-replaced
 * {@code ArmedModel} entity. This does not draw items itself: vanilla
 * {@code ItemInHandLayer} still owns the main/off-hand mapping, display context, left-hand flag
 * and the interaction/attack poses. Only the final hand translation is replaced.
 *
 * <p>All vectors here are model pixels (16 px = 1 block) unless a name ends in {@code Blocks}.
 * Item meshes are never scaled by the penguin body scale; only their anchor position follows it.
 */
public final class PenguinHeldItemRenderer {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    /**
     * Visible end of the last wing segment relative to the tip joint bind. At rest this is
     * (8.8, 12, 0.7) for the right wing, matching the reviewed diagnostic.
     */
    private static final Vector3f ACTIVE_TIP_OFFSET = new Vector3f(0.78F, 2.0F, 0.17F);
    /** The same visible end relative to the wing root ModelPart, used outside the taunt clip. */
    private static final Vector3f IDLE_WING_OFFSET = new Vector3f(3.35F, 9.4F, 0.5F);
    /** Fixed translation applied by vanilla after {@code translateToHand}, in pixels. */
    private static final Vector3f VANILLA_HAND_OFFSET = new Vector3f(1.0F, 2.0F, -10.0F);

    private PenguinHeldItemRenderer() {
    }

    /** True wing-end position in canonical model blocks and the wing rotation, no vanilla tail. */
    private record Grip(Vector3f handPixels, Quaternionf rotation) {
    }

    static Vector3f visibleTipOffset(boolean positiveWing) {
        Vector3f offset=new Vector3f(ACTIVE_TIP_OFFSET);
        if(!positiveWing)offset.x=-offset.x;
        return offset;
    }

    private static Grip grip(PenguinProportions.Profile profile, PenguinAnimationPose pose,
                             PenguinBodyModel penguin, HumanoidArm arm) {
        boolean left = arm == HumanoidArm.LEFT;
        // Legacy left_wing is on anatomical right (negative Java-model X).
        boolean negativeWing = !left;
        Matrix4f wingFrame;
        Vector3f localOffset = new Vector3f();
        if (pose != null) {
            wingFrame = pose.world[negativeWing ? PenguinRig.LEFT_WING + 2 : PenguinRig.RIGHT_WING + 2];
            localOffset.set(ACTIVE_TIP_OFFSET);
        } else {
            ModelPart wing = negativeWing ? penguin.leftWing() : penguin.rightWing();
            wingFrame = PenguinAnimationPose.part(wing);
            localOffset.set(IDLE_WING_OFFSET);
        }
        if (negativeWing) {
            localOffset.x = -localOffset.x;
        }
        return new Grip(wingFrame.transformPosition(localOffset),
                wingFrame.getUnnormalizedRotation(new Quaternionf()).normalize());
    }

    /**
     * Shared true-grip extraction for the native Gecko NPC bridge: position in canonical model
     * blocks and wing rotation only. It deliberately does NOT pre-subtract vanilla's hand tail,
     * because a native bone locator replaces the whole item frame rather than a vanilla redirect.
     * The existing player/maid {@link #apply} path keeps its exact residual-subtraction semantics.
     */
    public static Matrix4f gripMatrix(PenguinProportions.Profile profile,
                                      PenguinAnimationPose pose, PenguinBodyModel penguin,
                                      HumanoidArm arm) {
        Grip grip = grip(profile, pose, penguin, arm);
        float scale = profile.bodyScale() / PIXELS_PER_BLOCK;
        return new Matrix4f().translation(grip.handPixels().x * scale,
                        grip.handPixels().y * scale + profile.bodyOffsetBlocks(),
                        grip.handPixels().z * scale)
                .rotate(grip.rotation());
    }

    /**
     * Appends the current wing-end frame to the layer's pose stack. The vanilla trailing
     * XP(-90)/YP(180)/translate sequence stays untouched; its fixed translation is pre-subtracted
     * here so the item origin lands on the wing end instead of the old humanoid hand.
     */
    static void apply(PoseStack poseStack, PenguinProportions.Profile profile,
                      PenguinAnimationPose pose, PenguinBodyModel penguin, HumanoidArm arm) {
        boolean left = arm == HumanoidArm.LEFT;
        Grip grip = grip(profile, pose, penguin, arm);
        Vector3f handPixels = grip.handPixels();
        Quaternionf wingRotation = grip.rotation();

        Quaternionf held = new Quaternionf(wingRotation)
                .mul(Axis.XP.rotationDegrees(-90.0F))
                .mul(Axis.YP.rotationDegrees(180.0F));
        Vector3f residualPixels = new Vector3f(VANILLA_HAND_OFFSET);
        if (left) {
            residualPixels.x = -residualPixels.x;
        }
        Vector3f residual = held.transform(residualPixels);

        float scale = profile.bodyScale() / PIXELS_PER_BLOCK;
        poseStack.translate(
                handPixels.x * scale - residual.x / PIXELS_PER_BLOCK,
                handPixels.y * scale - residual.y / PIXELS_PER_BLOCK
                        + profile.bodyOffsetBlocks(),
                handPixels.z * scale - residual.z / PIXELS_PER_BLOCK);
        poseStack.mulPose(wingRotation);
    }
}
