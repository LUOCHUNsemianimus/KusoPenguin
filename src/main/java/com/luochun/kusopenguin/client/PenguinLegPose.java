package com.luochun.kusopenguin.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared anatomical rebind for the two connected feathered legs.
 *
 * <p>All positions are penguin MODEL PIXELS. The incoming leg world rotations are authoritative
 * native/clip rotations and are kept unchanged; only their translation chain is replaced by the
 * short anatomical controls below. The legacy clip skeleton keeps its immutable hip 15 /
 * knee 20.5 / foot 22.3 bind, because keyframes and baked skins still reference that contract.
 * The feathered thigh, however, attaches to the pelvis and must rotate around pivots inside the
 * torso, not around the old exposed-pole bind. A standard pivot rebinding keeps both contracts:
 * {@code skin = world * T(-clipBind) = controlWorld * T(-controlBind)} at any pose.
 *
 * <p>Positive-X is the right leg, negative-X the left. The pivots are mirrored so both legs stay
 * symmetric. Global/entity, pelvis, chest, head, wing and body matrices are never touched.
 */
final class PenguinLegPose {
    /** Anatomical control pivots of the connected right leg, in model pixels. */
    private static final Vector3f RIGHT_HIP = new Vector3f(3.3F, 20.0F, 0.0F);
    private static final Vector3f RIGHT_KNEE = new Vector3f(3.3F, 21.3F, 0.0F);
    private static final Vector3f RIGHT_FOOT = new Vector3f(3.3F, 22.3F, 0.0F);
    /** Negative-X mirror of the right leg controls. */
    private static final Vector3f LEFT_HIP = new Vector3f(-3.3F, 20.0F, 0.0F);
    private static final Vector3f LEFT_KNEE = new Vector3f(-3.3F, 21.3F, 0.0F);
    private static final Vector3f LEFT_FOOT = new Vector3f(-3.3F, 22.3F, 0.0F);

    private PenguinLegPose() {
    }

    /**
     * Rewrites the hip/knee/foot world matrices in place. Call exactly once per pose, before the
     * skin matrices are generated. {@code world} is in model pixels and is not shared with the
     * immutable clip data.
     */
    static void apply(Matrix4f[] world, PenguinAnimationClip clip) {
        // The pelvis skin frame is the physical pelvis after the clip's own bind offset has been
        // cancelled. Its scale is the intended common body scale; translations and rotations in
        // the clip are deliberately not reused as leg anchors.
        Matrix4f pelvisSkin = new Matrix4f(world[PenguinRig.PELVIS])
                .translate(clip.bind(PenguinRig.PELVIS).negate());
        Vector3f bodyScale = pelvisSkin.getScale(new Vector3f());
        rebind(world, clip, pelvisSkin, bodyScale, PenguinRig.RIGHT_HIP,
                RIGHT_HIP, RIGHT_KNEE, RIGHT_FOOT);
        rebind(world, clip, pelvisSkin, bodyScale, PenguinRig.LEFT_HIP,
                LEFT_HIP, LEFT_KNEE, LEFT_FOOT);
    }

    private static void rebind(Matrix4f[] world, PenguinAnimationClip clip, Matrix4f pelvisSkin,
                               Vector3f bodyScale, int hip,
                               Vector3f controlHip, Vector3f controlKnee, Vector3f controlFoot) {
        // Read all absolute incoming rotations BEFORE overwriting the chain. They already contain
        // the native parent/root rotation, so no parent rotation is compounded a second time.
        Quaternionf hipRotation = rotation(world[hip]);
        Quaternionf kneeRotation = rotation(world[hip + 1]);
        Quaternionf footRotation = rotation(world[hip + 2]);

        // The hip anchor is the anatomical pelvis point, not the human/independent-root bone
        // translation. Limb lengths are the short control offsets, subject only to body scale.
        Vector3f hipPosition = pelvisSkin.transformPosition(new Vector3f(controlHip));
        Matrix4f hipWorld = new Matrix4f().translation(hipPosition).rotate(hipRotation)
                .scale(bodyScale);
        Vector3f kneePosition = hipWorld.transformPosition(
                new Vector3f(controlKnee).sub(controlHip));
        Matrix4f kneeWorld = new Matrix4f().translation(kneePosition).rotate(kneeRotation)
                .scale(bodyScale);
        Vector3f footPosition = kneeWorld.transformPosition(
                new Vector3f(controlFoot).sub(controlKnee));
        Matrix4f footWorld = new Matrix4f().translation(footPosition).rotate(footRotation)
                .scale(bodyScale);

        // Standard pivot rebinding for the immutable clip skin contract. At neutral each control world is its anatomical bind frame; rebinding restores
        // the original clip world, so every resulting skin matrix stays identity (or the
        // common native body transform) and the rest model does not pop.
        world[hip] = rebindFrame(hipWorld, controlHip, clip.bind(hip));
        world[hip + 1] = rebindFrame(kneeWorld, controlKnee, clip.bind(hip + 1));
        world[hip + 2] = rebindFrame(footWorld, controlFoot, clip.bind(hip + 2));
    }

    private static Matrix4f rebindFrame(Matrix4f controlWorld, Vector3f controlBind,
                                        Vector3f clipBind) {
        return new Matrix4f(controlWorld)
                .translate(-controlBind.x, -controlBind.y, -controlBind.z)
                .translate(clipBind);
    }

    private static Quaternionf rotation(Matrix4f matrix) {
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }
}
