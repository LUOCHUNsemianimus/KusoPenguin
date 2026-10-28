package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinRig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;

/**
 * Shared read-only {@link ModelPart} rig for the optional native-animated boss bridges.
 *
 * <p>Only immutable metadata (the ancestor chain of each joint, the alignment matrix and the
 * rest-pose validation) is retained. Every draw recomposes the current native matrices from the
 * already-evaluated {@link ModelPart} fields; no posed matrix is cached or shared between
 * entities, and no native translation/rotation/visibility is written.
 *
 * <p>Coordinates stay in native model pixels with native Y pointing down. The penguin skin's own
 * {@code /16} conversion happens later in {@code PenguinSkinnedMesh#render}.
 */
final class NativeModelPartRig {
    static final float REST_EPSILON = 1.0E-4F;

    private final JointPath[] joints;
    private final Matrix4f alignment;

    private NativeModelPartRig(JointPath[] joints, Matrix4f alignment) {
        this.joints = joints;
        this.alignment = alignment;
    }

    /**
     * Resolves all 16 penguin joints from exact ancestor-name paths. Any missing or renamed bone
     * returns null, which makes the caller replay the complete native renderer.
     */
    static NativeModelPartRig create(ModelPart root, String[][] jointNames, Matrix4f alignment) {
        if (root == null || jointNames == null || jointNames.length != PenguinRig.COUNT) {
            return null;
        }
        JointPath[] joints = new JointPath[PenguinRig.COUNT];
        for (int i = 0; i < joints.length; i++) {
            joints[i] = jointNames[i] == null ? null : path(root, jointNames[i]);
            if (joints[i] == null) {
                return null;
            }
        }
        return new NativeModelPartRig(joints, new Matrix4f(alignment));
    }

    /**
     * Same formula used by the accepted Maledictus bridge: {@code D = F * inverse(B)} cancels the
     * native rest pose, then alignment and the immutable penguin bind are applied.
     */
    Matrix4f[] world(PenguinAnimationClip bind) {
        Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
        for (int i = 0; i < world.length; i++) {
            Matrix4f delta = new Matrix4f(joints[i].current())
                    .mul(new Matrix4f(joints[i].initial()).invert());
            // world = D * A * T(bind): the skin receives exactly D * A in native pixels.
            world[i] = delta.mul(alignment()).translate(bind.bind(i));
        }
        return world;
    }

    Matrix4f alignment() {
        return new Matrix4f(alignment);
    }

    /** Builds one immutable root-to-terminal ancestor path, or null when a name is absent. */
    static JointPath path(ModelPart root, String... names) {
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

    static ModelPart child(ModelPart parent, String name) {
        return parent != null && parent.hasChild(name) ? parent.getChild(name) : null;
    }

    /** Validates only the immutable rest translation; animated state is sampled per draw. */
    static boolean rest(ModelPart part, float x, float y, float z) {
        if (part == null) {
            return false;
        }
        PartPose pose = part.getInitialPose();
        return Math.abs(pose.x - x) <= REST_EPSILON
                && Math.abs(pose.y - y) <= REST_EPSILON
                && Math.abs(pose.z - z) <= REST_EPSILON;
    }

    static String[] append(String[] path, String child) {
        String[] result = new String[path.length + 1];
        System.arraycopy(path, 0, result, 0, path.length);
        result[path.length] = child;
        return result;
    }

    /**
     * Full root-to-terminal ancestor chain. Rest uses {@link ModelPart#getInitialPose()};
     * animated state uses the current translation, rotation and scale.
     */
    static final class JointPath {
        private final ModelPart[] chain;

        private JointPath(ModelPart[] chain) {
            this.chain = chain;
        }

        ModelPart[] chain() {
            return chain;
        }

        ModelPart terminal() {
            return chain[chain.length - 1];
        }

        Matrix4f current() {
            return chain(false);
        }

        Matrix4f initial() {
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
