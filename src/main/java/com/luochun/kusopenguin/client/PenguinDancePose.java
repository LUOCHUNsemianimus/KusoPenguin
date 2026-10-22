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
    /** Pose hold rate. Only sampling uses this grid; gameplay, audio and input stay continuous. */
    private static final double DANCE_FPS = 30.0;
    /** Authored choreography runs 1.5x faster than the real five-second style clock. */
    private static final float MOTION_SPEED = 1.5F;
    private static final float HUG_ELEVATION = (float) Math.toRadians(30.0);
    /** Forearm bend away from the upper-wing line; 20 degrees gives a 160 degree included angle. */
    private static final float HUG_ELBOW_BEND = (float) Math.toRadians(20.0);
    /** Relaxed pole arms stay nearly forward: the swing is 25 degrees instead of the wave's 45. */
    private static final float POLE_SWING = (float) Math.toRadians(25.0);
    /** Same slight 20 degree forearm bend away from straight, used by both open pole arms. */
    private static final float POLE_ELBOW_BEND = (float) Math.toRadians(20.0);
    /** Wave torso sway in degrees, tied to the same accelerated arm cycle. */
    private static final float WAVE_PITCH = (float) Math.toRadians(6.0);
    private static final float WAVE_ROLL = (float) Math.toRadians(4.0);
    /** Visible wave bob in penguin model pixels; model +Y points down. */
    private static final float WAVE_BOB = 1.0F;
    private static final float HIP_SETBACK = 5.0F;
    /** Jelly height stays inside 0.8..1.2; width is the inverse square root to keep volume. */
    private static final float JELLY_HEIGHT = 0.2F;

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
        return evaluateInternal(elapsedSeconds, parts, original, profile, false);
    }

    /** Revival channel: forced pole style with one continuous motion clock for the full 8 s. */
    public static PenguinAnimationPose evaluatePole(double elapsedSeconds, PenguinBodyModel parts,
                                                     EntityModel<?> original) {
        PenguinProportions.Profile profile = PenguinProportions.forModel(original);
        return profile == null ? null
                : evaluatePole(elapsedSeconds, parts, original, profile);
    }

    public static PenguinAnimationPose evaluatePole(double elapsedSeconds, PenguinBodyModel parts,
                                                     EntityModel<?> original,
                                                     PenguinProportions.Profile profile) {
        return evaluateInternal(elapsedSeconds, parts, original, profile, true);
    }

    private static PenguinAnimationPose evaluateInternal(double elapsedSeconds,
                                                          PenguinBodyModel parts,
                                                          EntityModel<?> original,
                                                          PenguinProportions.Profile profile,
                                                          boolean pole) {
        if (!Double.isFinite(elapsedSeconds)) throw new IllegalArgumentException("Non-finite dance clock");
        PenguinAnimationClip bind = PenguinMeshModel.animationClip();
        if (bind == null) return null; // The resource cache has not loaded yet.
        // Quantize only pose sampling; never throttle rendering, music, entity ticks or input.
        double sampledSeconds = Math.floor(elapsedSeconds * DANCE_FPS) / DANCE_FPS;
        // Disc styles keep their real five-second cadence. The revival channel stays on pole
        // (style 1) and uses the continuous elapsed clock, so it never switches at five seconds.
        int dance = pole ? 1 : danceIndex(sampledSeconds);
        // Separate motion clock: the authored 0.5 s hip/wave cycle becomes 1/3 s and the
        // 1 s pole cycle becomes 2/3 s, while disc styles change every five real seconds.
        // Subtract the real style counter, not danceIndex (0..2): modulo 5 makes every style
        // start at phase zero again, instead of accumulating 15 s of phase across style wraps.
        double styleElapsed = pole ? sampledSeconds
                : sampledSeconds - Math.floor(sampledSeconds / 5.0) * 5.0;
        float motionSeconds = (float) (styleElapsed * MOTION_SPEED);
        float cycle = TAU * motionSeconds * (dance == 1 ? 1.0F : 2.0F);
        // Standard sine/cosine values reach the authored angles at 30 FPS without old 5 FPS
        // plateau normalization or clipping.
        float wave = Math.clamp((float) Math.sin(cycle), -1.0F, 1.0F);
        float crouch = dance == 0 ? .5F * (1.0F - (float) Math.cos(cycle))
                : dance == 1 ? 1.0F - Math.abs(wave) : 1.0F;
        float yaw = dance == 1 ? QUARTER_PI * wave : 0.0F;
        float x = dance == 1 ? POLE_RADIUS * (float) Math.sin(yaw) : 0.0F;
        float z = dance == 1 ? -POLE_RADIUS + POLE_RADIUS * (float) Math.cos(yaw)
                : dance == 0 ? 1.4F * wave : 0.0F;
        // A small local setback keeps the forward-leaning shoulders near the column even though
        // the old fixed-palm constraint is gone. At the standing endpoints crouch=0 keeps the
        // previous body frame.
        z += HIP_SETBACK * crouch;
        float drop = 2.1F * crouch + (dance == 2 ? WAVE_BOB * wave : 0.0F);
        // Model +Y points down and -Z points forward: positive X pitch leans FORWARD.
        float lean = QUARTER_PI * crouch;
        float roll = 0.0F;
        if (dance == 2) {
            // Pitch 45 +/-6 degrees and roll +/-4 degrees follow the same 1.5x arm cycle.
            // Quadrature sine/cosine makes a gentle sway rather than wall-clock noise.
            lean += WAVE_PITCH * wave;
            roll = WAVE_ROLL * (float) Math.cos(cycle);
        }

        Matrix4f[] world = neutral(bind);
        world[1].translation(x, 16.0F + drop, z).rotateY(yaw).rotateX(lean).rotateZ(roll);
        world[2].set(world[1]).translate(0, -4, 0);
        world[3].set(world[2]).translate(0, -12, 0);
        // Fit only the torso before solving the limbs. The pole palms are allowed to move, but
        // a whole-rig lift after arm IK would still distort the authored limb directions.
        PenguinAnimationPose provisional = PenguinAnimationPose.fromDance(world, new Matrix4f(), bind);
        PenguinSkinnedMesh skin = PenguinMeshModel.currentSkin();
        if (skin != null) {
            float lift = Math.min(0.0F, FLOOR_Y - skin.maxY(provisional));
            for (int joint = 1; joint <= 3; joint++) world[joint].m31(world[joint].m31() + lift);
        }
        for (int side : new int[]{1, -1}) {
            int wing = side > 0 ? 4 : 7;
            Vector3f shoulder = world[2].transformPosition(bind.bind(wing).sub(bind.bind(2)));
            if (dance == 0) {
                solveHeadHug(world, bind, wing, side, shoulder);
            } else if (dance == 1) {
                // The old fixed-palm pole IK is deliberately relaxed: both wings now open
                // forward with a modest 25 degree swing. Keeping the direction in entity
                // space still leaves the hands in front / toward the column while the chest
                // orbits +/-45 degrees, so the around-column read is preserved.
                float poleSwing = POLE_SWING * wave;
                Vector3f upper = new Quaternionf().rotateX(poleSwing)
                        .transform(new Vector3f(0, 0, -1));
                Vector3f lower = new Quaternionf().rotateX(poleSwing + POLE_ELBOW_BEND)
                        .transform(new Vector3f(0, 0, -1));
                // Tip follows the forearm; no extra wrist fold is added on top of the 20 deg bend.
                solveArmDirections(world, bind, wing, shoulder, upper, lower,
                        forearmRotation(bind, wing, lower));
            } else {
                float armAngle = side * QUARTER_PI * wave;
                Quaternionf swing = new Quaternionf().rotateX(armAngle);
                Vector3f forward = swing.transform(new Vector3f(0, 0, -1));
                solveArmDirections(world, bind, wing, shoulder, forward, forward,
                        new Quaternionf().rotateX(-HALF_PI + armAngle));
            }
            int hip = side > 0 ? 10 : 13;
            Vector3f hipPosition = world[1].transformPosition(
                    new Vector3f(side * 3.3F, -1.0F, 0));
            Vector3f foot = new Vector3f(x + side * 3.3F, 22.3F, z);
            solveChain(world, bind, hip, hipPosition, foot, new Vector3f(0, 0, -1));
            world[hip + 2].translation(foot);
        }

        Matrix4f nativeHead = PenguinHeadRenderer.headMatrix(original);
        Quaternionf nativeRotation = nativeHead.getUnnormalizedRotation(new Quaternionf()).normalize();
        // Cache the unscaled chest rotation BEFORE jelly. Head position follows the deformed
        // neck, but its orientation must stay rigid and must not inherit scale/shear.
        Quaternionf chestRotation = world[2].getUnnormalizedRotation(new Quaternionf()).normalize();

        // Jelly only while evaluating a dance pose, in model pixels. Standing/high phases
        // stretch (height 1.2, narrower), crouch/low phases compress (height .8, wider);
        // the wave uses its actual bob sign. Anchoring at floor Y=24 keeps the foot contact
        // point fixed, so no entity hop, physics step or hitbox change is involved.
        float stretch = dance == 2 ? -wave : 1.0F - 2.0F * crouch;
        float heightScale = Math.clamp(1.0F + JELLY_HEIGHT * stretch,
                1.0F - JELLY_HEIGHT, 1.0F + JELLY_HEIGHT);
        float widthScale = (float) (1.0 / Math.sqrt(heightScale));
        Matrix4f jelly = new Matrix4f()
                .translate(0.0F, FLOOR_Y, 0.0F)
                .scale(widthScale, heightScale, widthScale)
                .translate(0.0F, -FLOOR_Y, 0.0F);
        // Left-multiply every world matrix, root included, so fromDance's skin = world *
        // inverse bind deforms the visible mesh and the held-item tip anchor together.
        for (int joint = 0; joint < world.length; joint++) {
            world[joint] = new Matrix4f(jelly).mul(world[joint]);
        }

        Vector3f headBlocks = world[3].getTranslation(new Vector3f())
                .mul(profile.bodyScale() / 16.0F).add(0, profile.bodyOffsetBlocks(), 0);
        // Rigid native head: translation from the deformed neck, unit size, cached upright
        // chest rotation times the native head rotation. Never scale the whole entity/head layer.
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

    /**
     * Places both wing links from authored entity-local directions and preserves their bind
     * lengths. Head-hug and open pole arms pass a forearm-following tip frame; the wave keeps
     * its held-item orientation.
     */
    private static void solveArmDirections(Matrix4f[] world, PenguinAnimationClip bind, int joint,
                                           Vector3f shoulder, Vector3f upperDirection,
                                           Vector3f lowerDirection, Quaternionf tipRotation) {
        Vector3f first = bind.bind(joint + 1).sub(bind.bind(joint));
        Vector3f second = bind.bind(joint + 2).sub(bind.bind(joint + 1));
        Vector3f upper = new Vector3f(upperDirection).normalize(first.length());
        Vector3f lower = new Vector3f(lowerDirection).normalize(second.length());
        Vector3f elbow = new Vector3f(shoulder).add(upper);
        world[joint].translation(shoulder).rotate(new Quaternionf().rotationTo(first, upper));
        world[joint + 1].translation(elbow)
                .rotate(new Quaternionf().rotationTo(second, lower));
        world[joint + 2].translation(new Vector3f(elbow).add(lower)).rotate(tipRotation);
    }

    /** Forearm world rotation used when the tip frame should follow the forearm. */
    private static Quaternionf forearmRotation(PenguinAnimationClip bind, int joint,
                                               Vector3f lowerDirection) {
        Vector3f second = bind.bind(joint + 2).sub(bind.bind(joint + 1));
        return new Quaternionf().rotationTo(second, new Vector3f(lowerDirection).normalize());
    }

    /** Author the upper wing 30 degrees above horizontal and bend only 20 degrees at the elbow. */
    private static void solveHeadHug(Matrix4f[] world, PenguinAnimationClip bind, int joint, int side,
                                      Vector3f shoulder) {
        Vector3f upper = new Vector3f(side * (float) Math.cos(HUG_ELEVATION),
                -(float) Math.sin(HUG_ELEVATION), 0.0F);
        float forearmElevation = HUG_ELEVATION - HUG_ELBOW_BEND;
        Vector3f lower = new Vector3f(side * (float) Math.cos(forearmElevation),
                -(float) Math.sin(forearmElevation), 0.0F);
        // The tip frame follows the forearm, replacing the old extra ~90 degree wrist fold.
        solveArmDirections(world, bind, joint, shoulder, upper, lower,
                forearmRotation(bind, joint, lower));
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
