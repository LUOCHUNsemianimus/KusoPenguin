package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.client.dance.DanceClient;
import com.luochun.kusopenguin.dance.Source;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Authoritative Cataclysm boss dance sampling and placement.
 *
 * <p>The server-owned {@link DanceClient} state is the only playback authority. Any selected
 * builtin (same style mapping as ordinary dances) or cached uploaded clip is sampled, and only for
 * an exact {@link Source#MANUAL} playback. The shared {@link PenguinRenderContext} clip sampler is
 * fed a dedicated neutral {@link PenguinBodyModel} plus a fixed valid dummy profile, because the
 * boss renderers have no humanoid head to drive. Only {@link PenguinAnimationPose#world} is
 * consumed; the humanoid head delta is never used.
 *
 * <p>All coordinates stay in penguin model pixels. The placement maps the penguin bind pelvis
 * frame onto the ordinary boss skin pelvis frame:
 * {@code P = ordinaryPelvisWorld * inverse(T(bindPelvis))}, so a placed joint is
 * {@code P * sampledWorld[joint]}. When the sampled pose is neutral this is exactly the ordinary
 * boss skin transform, which keeps the non-dance path unchanged.
 */
public final class BossDancePose {
    private static final PenguinBodyModel NEUTRAL_BODY =
            new PenguinBodyModel(PenguinBodyModel.createBodyLayer().bakeRoot());
    /**
     * The dance evaluator only needs a finite, non-null humanoid profile for its discarded head
     * delta and for the procedural wave parser. None of these values reach the boss skin because
     * only sampled world matrices are consumed.
     */
    private static final PenguinProportions.Profile DUMMY_PROFILE =
            new PenguinProportions.Profile(8.0F, 24.0F, 1.0F, 0.0F, 0.0F, 24.0F);
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private BossDancePose() {
    }

    /**
     * Samples the current authoritative selected playback and returns both the raw sampled world
     * matrices and the affine placement derived from the ordinary boss pelvis. Returns {@code null}
     * whenever the exact source/clip gate or any shared asset is unavailable, so the caller keeps
     * its complete native path.
     */
    public static Sample sample(LivingEntity entity, Matrix4f ordinaryPelvis,
                                PenguinAnimationClip bind) {
        String clipId = entity == null ? null : DanceClient.clip(entity);
        if (entity == null || ordinaryPelvis == null || bind == null
                || !DanceClient.active(entity, Source.MANUAL)
                || clipId == null || clipId.isEmpty()
                || !PenguinMeshModel.ready()) {
            return null;
        }
        // Scylla does not pass through LivingEntityRenderer, so the game clock is the one shared
        // partial tick for all three boss renderers.
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double elapsed = DanceClient.elapsed(entity, partial);
        if (elapsed < 0.0D || !Double.isFinite(elapsed)) {
            return null;
        }
        // Dedicated neutral controls: reset before and after because the evaluator is shared with
        // real player/maid draws and must never retain another actor's limb pose.
        NEUTRAL_BODY.resetPose();
        PenguinAnimationPose pose;
        try {
            pose = PenguinRenderContext.selectedDancePose(
                    clipId, (float) elapsed, NEUTRAL_BODY, NEUTRAL_BODY, DUMMY_PROFILE);
        } finally {
            NEUTRAL_BODY.resetPose();
        }
        if (pose == null) {
            return null;
        }
        Matrix4f placement = new Matrix4f(ordinaryPelvis)
                .mul(new Matrix4f().translation(bind.bind(PenguinRig.PELVIS)).invert());
        return new Sample(pose.world, placement);
    }

    /** Convert a model-pixel affine into the block-scaled PoseStack frame. */
    public static Matrix4f toBlocks(Matrix4f pixels) {
        Matrix4f blocks = new Matrix4f(pixels);
        blocks.m30(blocks.m30() / PIXELS_PER_BLOCK);
        blocks.m31(blocks.m31() / PIXELS_PER_BLOCK);
        blocks.m32(blocks.m32() / PIXELS_PER_BLOCK);
        return blocks;
    }

    private static Quaternionf rotation(Matrix4f matrix) {
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    /** One draw's raw dance sample plus the boss-placement affine; no state is cached. */
    public static final class Sample {
        private final Matrix4f[] world;
        private final Matrix4f placement;

        private Sample(Matrix4f[] world, Matrix4f placement) {
            this.world = world;
            this.placement = placement;
        }

        /** Sampled joint matrices moved onto the ordinary boss pelvis/size frame. */
        public Matrix4f[] placed() {
            Matrix4f[] result = new Matrix4f[world.length];
            for (int joint = 0; joint < result.length; joint++) {
                result[joint] = new Matrix4f(placement).mul(world[joint]);
            }
            return result;
        }

        /**
         * Sampled chest-relative wing rotation. Ignis belly flippers and the native-arm shoulder
         * delta must consume exactly this one quaternion instead of recomputing a variant.
         */
        public Quaternionf wingRotation(int wing) {
            return new Quaternionf(rotation(world[PenguinRig.CHEST]))
                    .conjugate()
                    .mul(rotation(world[wing]))
                    .normalize();
        }

        /**
         * Per-side dance wing rotation relative to the sampled chest, expressed as a rotation
         * around the supplied ordinary native shoulder joint. The conjugation keeps the arm's
         * existing bind/current local axes and leaves the shoulder point fixed:
         * {@code G = T(S) * Rj * qLocal * Rj^-1 * T(-S)}.
         */
        public Matrix4f shoulderDelta(int wing, Matrix4f shoulder) {
            Quaternionf qLocal = wingRotation(wing);
            Matrix4f joint = new Matrix4f(shoulder);
            Quaternionf jointRotation = rotation(joint);
            Quaternionf worldRotation = new Quaternionf(jointRotation)
                    .mul(qLocal)
                    .mul(new Quaternionf(jointRotation).conjugate())
                    .normalize();
            Vector3f shoulderPoint = joint.getTranslation(new Vector3f());
            return new Matrix4f()
                    .translation(shoulderPoint)
                    .rotate(worldRotation)
                    .translate(-shoulderPoint.x, -shoulderPoint.y, -shoulderPoint.z);
        }

        /**
         * Rigid neck attachment for a retained native head subtree.
         *
         * <p>The target is the placed dance head pivot; because {@link PenguinRig#HEAD} is the neck
         * pivot authored at the zero bind offset, {@code placement * sampled HEAD world} carries the
         * exact dance pivot and orientation. The native head already owns its look and scale, so the
         * correction only translates that pivot and adds the dance rotation relative to the placement
         * frame:
         * {@code T(target) * R(placedHead) * inverse(R(placement)) * T(-currentNativeHeadPosition)}.
         * It deliberately ignores the sampled head scale, so a stretched penguin head cannot leak
         * into the native head. Returned in native model pixels; convert once with {@link #toBlocks}.
         */
        public Matrix4f headDelta(Matrix4f currentNativeHead) {
            if (currentNativeHead == null) {
                return new Matrix4f();
            }
            Matrix4f placedHead = new Matrix4f(placement).mul(world[PenguinRig.HEAD]);
            Quaternionf danceRotation = new Quaternionf(rotation(placedHead))
                    .mul(new Quaternionf(rotation(placement)).conjugate())
                    .normalize();
            Vector3f target = placedHead.getTranslation(new Vector3f());
            Vector3f current = currentNativeHead.getTranslation(new Vector3f());
            return new Matrix4f()
                    .translation(target)
                    .rotate(danceRotation)
                    .translate(-current.x, -current.y, -current.z);
        }
    }
}
