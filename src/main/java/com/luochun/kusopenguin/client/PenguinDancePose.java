package com.luochun.kusopenguin.client;

import net.minecraft.client.model.EntityModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure procedural sampling; all rig positions are penguin model pixels, never world blocks. */
public final class PenguinDancePose {
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float QUARTER_PI = (float) (Math.PI / 4.0);
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float FLOOR_Y = 24.0F;
    private static final float POLE_RADIUS = 3.5F;

    private PenguinDancePose() {}

    public static int danceIndex(double elapsedSeconds) {
        return Math.floorMod((long) Math.floor(elapsedSeconds / 5.0), 3);
    }

    public static PenguinAnimationPose evaluate(double elapsedSeconds, PenguinBodyModel parts,
                                                 EntityModel<?> original) {
        PenguinProportions.Profile profile = PenguinProportions.forModel(original);
        return profile == null ? null : evaluate(elapsedSeconds, parts, original, profile);
    }

    /** Explicit profile overload also supports formerly prone families while dancing upright. */
    public static PenguinAnimationPose evaluate(double elapsedSeconds, PenguinBodyModel parts,
                                                 EntityModel<?> original,
                                                 PenguinProportions.Profile profile) {
        if (!Double.isFinite(elapsedSeconds)) throw new IllegalArgumentException("Non-finite dance clock");
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        if (bind == null) return null; // The resource cache has not loaded yet.
        int dance = danceIndex(elapsedSeconds);
        float t = (float) (elapsedSeconds % 15.0);
        float cycle = TAU * t * (dance == 1 ? 1.0F : 2.0F);
        float wave = (float) Math.sin(cycle);
        float crouch = dance == 0 ? (1.0F - (float) Math.cos(cycle)) * .5F
                : dance == 1 ? 1.0F - Math.abs(wave) : 1.0F;
        float yaw = dance == 1 ? QUARTER_PI * wave : 0.0F;
        float x = dance == 1 ? POLE_RADIUS * (float) Math.sin(yaw) : 0.0F;
        float z = dance == 1 ? -POLE_RADIUS + POLE_RADIUS * (float) Math.cos(yaw)
                : dance == 0 ? 1.4F * wave : 0.0F;
        float drop = 2.1F * crouch + (dance == 2 ? .35F * wave : 0.0F);
        float lean = dance == 0 ? -.28F * crouch : -.18F * crouch;

        Matrix4f[] world = neutral(bind);
        world[1].translation(x, 16.0F + drop, z).rotateY(yaw).rotateX(lean);
        world[2].set(world[1]).translate(0, -4, 0);
        world[3].set(world[2]).translate(0, -12, 0);
        // Fit only the torso before solving the limbs. A whole-rig lift after arm IK would move
        // the supposedly fixed pole palms and break the principal invariant of dance 2.
        PenguinAnimationPose provisional = PenguinAnimationPose.fromDance(world, new Matrix4f(), bind);
        PenguinSkinnedMesh skin = PenguinMeshModel.currentSkin();
        if (skin != null) {
            float lift = Math.min(0.0F, FLOOR_Y - skin.maxY(provisional));
            for (int joint = 1; joint <= 3; joint++) world[joint].m31(world[joint].m31() + lift);
        }
        for (int side : new int[]{1, -1}) {
            int wing = side > 0 ? 4 : 7;
            Vector3f shoulder = world[2].transformPosition(bind.bind(wing).sub(bind.bind(2)));
            Vector3f palm;
            Quaternionf palmRotation;
            if (dance == 0) {
                // Lower sides/back of the unchanged-size native head, rather than above its top.
                palm = world[3].transformPosition(new Vector3f(side * 4.7F, -3.8F, 2.0F));
                palmRotation = new Quaternionf().rotateZ(side * 1.6F).rotateX(.45F);
            } else if (dance == 1) {
                // These two positions deliberately DO NOT use any body/chest matrix. Their
                // midpoint and both visible palms stay fixed in entity-local space at +/-45°.
                palm = new Vector3f(side * 2.8F, 8.0F, -POLE_RADIUS);
                palmRotation = new Quaternionf().rotateX(-HALF_PI);
            } else {
                float armAngle = side * QUARTER_PI * wave;
                Vector3f reach = new Quaternionf().rotateX(armAngle)
                        .transform(new Vector3f(0, 0, -8.0F));
                palm = new Vector3f(shoulder).add(reach);
                palmRotation = new Quaternionf().rotateX(-HALF_PI + armAngle);
            }
            solveWing(world, bind, wing, side, shoulder, palm, palmRotation);
            int hip = side > 0 ? 10 : 13;
            Vector3f hipPosition = world[1].transformPosition(
                    new Vector3f(side * 3.3F, -1.0F, 0));
            Vector3f foot = new Vector3f(x + side * 3.3F, 22.3F, z);
            solveChain(world, bind, hip, hipPosition, foot, new Vector3f(0, 0, -1));
            world[hip + 2].translation(foot);
        }

        Matrix4f nativeHead = PenguinHeadRenderer.headMatrix(original);
        Quaternionf nativeRotation = nativeHead.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf chestRotation = world[2].getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f headBlocks = world[3].getTranslation(new Vector3f())
                .mul(profile.bodyScale() / 16.0F).add(0, profile.bodyOffsetBlocks(), 0);
        // The original head receives rigid translation/rotation only, never the body scale.
        Matrix4f headDelta = new Matrix4f().translation(headBlocks)
                .rotate(chestRotation.mul(nativeRotation)).mul(new Matrix4f(nativeHead).invert());
        return PenguinAnimationPose.fromDance(world, headDelta, bind);
    }

    /** Used by geometry checks and by the same visible tip convention as held item attachment. */
    public static Vector3f palmPosition(PenguinAnimationPose pose, boolean positiveWing) {
        Vector3f offset = PenguinHeldItemRenderer.visibleTipOffset(positiveWing);
        return pose.world[positiveWing ? 6 : 9].transformPosition(offset);
    }

    private static Matrix4f[] neutral(PenguinAnimationClip bind) {
        Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
        for (int joint = 0; joint < world.length; joint++) world[joint] = new Matrix4f().translation(bind.bind(joint));
        return world;
    }

    private static void solveWing(Matrix4f[] world, PenguinAnimationClip bind, int joint, int side,
                                   Vector3f shoulder, Vector3f palm, Quaternionf palmRotation) {
        Vector3f offset = PenguinHeldItemRenderer.visibleTipOffset(side > 0);
        Vector3f wrist = new Vector3f(palm).sub(palmRotation.transform(offset));
        solveChain(world, bind, joint, shoulder, wrist, new Vector3f(side, 0, 1));
        world[joint + 2].translation(wrist).rotate(palmRotation);
    }

    /** Analytic two-link IK with a stable elbow/knee plane; each link keeps its bind length. */
    private static void solveChain(Matrix4f[] world, PenguinAnimationClip bind, int joint,
                                    Vector3f root, Vector3f target, Vector3f preferredBend) {
        Vector3f first = bind.bind(joint + 1).sub(bind.bind(joint));
        Vector3f second = bind.bind(joint + 2).sub(bind.bind(joint + 1));
        float a = first.length(), b = second.length();
        Vector3f direction = new Vector3f(target).sub(root);
        float requested = direction.length();
        if (requested < .0001F) direction.set(0, 1, 0); else direction.div(requested);
        float distance = Math.max(Math.abs(a - b) + .0001F, Math.min(a + b - .0001F, requested));
        Vector3f bend = new Vector3f(preferredBend).sub(new Vector3f(direction).mul(preferredBend.dot(direction)));
        if (bend.lengthSquared() < .00001F) {
            bend.set(0, 0, 1).sub(new Vector3f(direction).mul(direction.z));
            if (bend.lengthSquared() < .00001F) bend.set(1, 0, 0);
        }
        bend.normalize();
        float along = (a * a - b * b + distance * distance) / (2 * distance);
        float height = (float) Math.sqrt(Math.max(0, a * a - along * along));
        Vector3f elbow = new Vector3f(root).fma(along, direction).fma(height, bend);
        Vector3f reachable = new Vector3f(root).fma(distance, direction);
        world[joint].translation(root).rotate(new Quaternionf().rotationTo(first,
                new Vector3f(elbow).sub(root)));
        world[joint + 1].translation(elbow).rotate(new Quaternionf().rotationTo(second,
                new Vector3f(reachable).sub(elbow)));
        world[joint + 2].translation(reachable);
    }
}
