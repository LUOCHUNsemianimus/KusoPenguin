package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.client.compat.IronCastingCompatClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Native Iron casting pose for the penguin skinned rig.
 *
 * <p>The five penguin roots and their translations/rotations were already copied from the animated
 * humanoid model by {@link PenguinPoseAdapters} and corrected by {@link PenguinPlayerPosture};
 * this builder only reuses those ends and the existing 16-joint bind. It never calls
 * {@code updatePart} and never replays PlayerAnimator. All rig values are penguin model pixels
 * (16 px = 1 block); the one native pivot that PlayerAnimator expresses in blocks is converted
 * explicitly below. Left/right follows the existing historical contract:
 * {@code left_wing} sits at negative X and is the anatomical right wing.
 */
public final class PenguinCastingPose {
    /** PlayerAnimator's layer bend pivot, in renderer blocks (see IBendHelper javap). */
    private static final float BEND_PIVOT_BLOCKS = 0.375F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    /** Native chest/head offsets from the penguin bind, in model pixels. */
    private static final Vector3f CHEST_FROM_PELVIS = new Vector3f(0.0F, -4.0F, 0.0F);

    private PenguinCastingPose() {
    }

    /**
     * Builds one immutable pose from the currently prepared {@link PenguinBodyModel} parts. The
     * profile supplies the block conversion for the rigid head mount; the snapshot supplies the
     * final applier bends sampled once for this draw.
     */
    static PenguinAnimationPose evaluate(PenguinBodyModel parts, EntityModel<?> original,
                                         PenguinProportions.Profile profile,
                                         IronCastingCompatClient.Snapshot snapshot) {
        if (parts == null || profile == null || snapshot == null) {
            return null;
        }
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        if (bind == null) {
            return null;
        }
        // The old copyPart helper copies positions and rotations but not PlayerAnimator scales.
        // Re-apply those final scales to the shared penguin parts for this draw only; the global
        // vanilla model and the layer stack are untouched.
        retainNativeScales(parts, original);

        IronCastingCompatClient.Bend bodyBend = snapshot.bendEnabled()
                ? snapshot.body() : IronCastingCompatClient.Bend.NONE;
        IronCastingCompatClient.Bend leftArmBend = snapshot.bendEnabled()
                ? snapshot.leftArm() : IronCastingCompatClient.Bend.NONE;
        IronCastingCompatClient.Bend rightArmBend = snapshot.bendEnabled()
                ? snapshot.rightArm() : IronCastingCompatClient.Bend.NONE;
        IronCastingCompatClient.Bend leftLegBend = snapshot.bendEnabled()
                ? snapshot.leftLeg() : IronCastingCompatClient.Bend.NONE;
        IronCastingCompatClient.Bend rightLegBend = snapshot.bendEnabled()
                ? snapshot.rightLeg() : IronCastingCompatClient.Bend.NONE;

        Matrix4f nativeBody = PenguinAnimationPose.part(parts.body());
        float pivotPixels = BEND_PIVOT_BLOCKS * PIXELS_PER_BLOCK / profile.bodyScale();
        Matrix4f bodyBendFrame = bendFrame(bodyBend, pivotPixels);

        Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
        world[PenguinRig.ROOT] = new Matrix4f();
        // The native body pivot is the model origin, while the rig pelvis bind is (0,16,0). The
        // trailing bind translation makes skin = nativeBody, so the already-copied root rotation
        // and translation are not applied twice.
        world[PenguinRig.PELVIS] = new Matrix4f(nativeBody)
                .translate(bind.bind(PenguinRig.PELVIS));
        // PlayerAnimator stores torso+body as one body-part bend. It supplements the chest so the
        // upper torso bends while the pelvis/legs keep their native animated roots.
        world[PenguinRig.CHEST] = new Matrix4f(bodyBendFrame)
                .mul(world[PenguinRig.PELVIS])
                .translate(CHEST_FROM_PELVIS);
        world[PenguinRig.HEAD] = new Matrix4f(world[PenguinRig.CHEST])
                .translate(0.0F, -12.0F, 0.0F);

        // Historical part-name mapping: humanoid.leftArm -> right_wing (+X, anatomical left);
        // humanoid.rightArm -> left_wing (-X, anatomical right).
        wing(world, bind, parts.rightWing(), PenguinRig.RIGHT_WING, bodyBendFrame, leftArmBend);
        wing(world, bind, parts.leftWing(), PenguinRig.LEFT_WING, bodyBendFrame, rightArmBend);
        // Legs are not native upper parts, so the body bend never rotates them.
        leg(world, bind, parts.rightFoot(), PenguinRig.RIGHT_HIP, rightLegBend);
        leg(world, bind, parts.leftFoot(), PenguinRig.LEFT_HIP, leftLegBend);

        Matrix4f nativeHead = PenguinHeadRenderer.headMatrix(original);
        Quaternionf nativeHeadRotation = nativeHead.getUnnormalizedRotation(new Quaternionf())
                .normalize();
        // The native head is a root sibling with its own PlayerAnimator head update, so the torso
        // ModelPart Euler must not be multiplied in again. BipedEntityModelMixin.renderToBuffer
        // applies only the body-only BEND to upper parts; supplement that one and nothing else.
        IronCastingCompatClient.Bend layerBodyBend = snapshot.bendEnabled()
                ? snapshot.layerBody() : IronCastingCompatClient.Bend.NONE;
        Quaternionf headBend = bendRotation(layerBodyBend);
        Vector3f headBlocks = world[PenguinRig.HEAD].getTranslation(new Vector3f())
                .mul(profile.bodyScale() / PIXELS_PER_BLOCK)
                .add(0.0F, profile.bodyOffsetBlocks(), 0.0F);
        // Head geometry stays rigid: only its mount follows the bent chest; orientation is the
        // independently animated native head times the upper-layer body bend, and headDelta
        // cancels the original native head local matrix exactly once.
        Matrix4f headDelta = new Matrix4f().translation(headBlocks)
                .rotate(headBend.mul(nativeHeadRotation))
                .mul(new Matrix4f(nativeHead).invert());
        return PenguinAnimationPose.fromDance(world, headDelta, bind);
    }

    private static void wing(Matrix4f[] world, PenguinAnimationClip bind, ModelPart wing,
                             int root, Matrix4f bodyBendFrame,
                             IronCastingCompatClient.Bend bend) {
        int mid = root + 1;
        int tip = root + 2;
        Vector3f midOffset = bind.bind(mid).sub(bind.bind(root));
        Vector3f tipOffset = bind.bind(tip).sub(bind.bind(mid));
        // The wing root uses the already animated native ModelPart, then the native layer body
        // bend, so the pose matches the normalized upper-layer frame. The forearm rotates at the
        // elbow and the tip follows that forearm with no extra wrist fold.
        world[root] = new Matrix4f(bodyBendFrame).mul(PenguinAnimationPose.part(wing));
        world[mid] = new Matrix4f(world[root]).translate(midOffset)
                .rotate(bendRotation(bend));
        world[tip] = new Matrix4f(world[mid]).translate(tipOffset);
    }

    private static void leg(Matrix4f[] world, PenguinAnimationClip bind, ModelPart leg,
                            int hip, IronCastingCompatClient.Bend bend) {
        int knee = hip + 1;
        int foot = hip + 2;
        Vector3f kneeOffset = bind.bind(knee).sub(bind.bind(hip));
        Vector3f footOffset = bind.bind(foot).sub(bind.bind(knee));
        world[hip] = PenguinAnimationPose.part(leg);
        world[knee] = new Matrix4f(world[hip]).translate(kneeOffset)
                .rotate(bendRotation(bend));
        world[foot] = new Matrix4f(world[knee]).translate(footOffset);
    }

    private static void retainNativeScales(PenguinBodyModel parts, EntityModel<?> original) {
        if (!(original instanceof HumanoidModel<?> humanoid)) {
            return;
        }
        copyScale(humanoid.body, parts.body());
        // Same historical arm mapping as PenguinPoseAdapters.
        copyScale(humanoid.leftArm, parts.rightWing());
        copyScale(humanoid.rightArm, parts.leftWing());
        copyScale(humanoid.rightLeg, parts.rightFoot());
        copyScale(humanoid.leftLeg, parts.leftFoot());
    }

    private static void copyScale(ModelPart source, ModelPart target) {
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    private static Matrix4f bendFrame(IronCastingCompatClient.Bend bend, float pivotPixels) {
        if (bend.amount() == 0.0F) {
            return new Matrix4f();
        }
        return new Matrix4f().translate(0.0F, pivotPixels, 0.0F)
                .rotate(bendRotation(bend))
                .translate(0.0F, -pivotPixels, 0.0F);
    }

    /** Exact native axis: {@code (cos(-direction), 0, sin(-direction))}, amount in radians. */
    private static Quaternionf bendRotation(IronCastingCompatClient.Bend bend) {
        if (bend.amount() == 0.0F) {
            return new Quaternionf();
        }
        float axisAngle = -bend.direction();
        return new Quaternionf().rotateAxis(bend.amount(),
                (float) Math.cos(axisAngle), 0.0F, (float) Math.sin(axisAngle));
    }
}
