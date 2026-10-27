package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.PenguinBodyLayer;
import com.luochun.kusopenguin.client.PenguinBodyModel;
import com.luochun.kusopenguin.client.PenguinDeathClient;
import com.luochun.kusopenguin.client.PenguinPoseAdapter;
import com.luochun.kusopenguin.client.PenguinProportions;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Yes Steve Model 2.6.5 native-head bridge.
 *
 * <p>Outer facade: no YSM type appears in a signature, so a game without YSM (or a dedicated
 * server) never initialises the nested implementation. The pinned 2.6.5 obfuscated entry points
 * live only in {@link Impl}; every symbol is resolved once and any mismatch permanently falls back
 * to the complete native maid render.
 *
 * <p>Native mesh capture is local-space only: the YSM utility is invoked twice with an identity
 * pose (full model, then AllHead hidden) into {@link CaptureConsumer}. The body vertex multiset is
 * subtracted from the full multiset, leaving the exact head/hair quads. Only complete 4-vertex
 * quads are considered; native draw order and duplicate quads survive the subtraction.
 */
public final class YsmCompatClient {
    private static final String MAID_MOD_ID = "touhou_little_maid";
    private static final String UTIL_CLASS = "com.elfmcys.yesstevemodel.OoooOoo0o0000O0o00o0O0Oo";
    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/YsmCompat");

    private static final ThreadLocal<ArrayDeque<Boolean>> PUSHED =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile Boolean available;

    private YsmCompatClient() {
    }

    /** True only when both the maid mod and the pinned YSM classes are present. */
    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached == null) {
            cached = ModList.get().isLoaded(MAID_MOD_ID) && present(UTIL_CLASS);
            available = cached;
        }
        return cached;
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, YsmCompatClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Opens a nestable per-draw frame around the TLM maid renderer's geoRender delegation. Every
     * call pushes a marker so the matching {@link #exit(boolean)} stays balanced even when the
     * draw is not a YSM replacement.
     */
    public static void enter(Object maidRenderer, Object entity, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, float partialTick) {
        boolean pushed = false;
        if (isAvailable() && entity instanceof LivingEntity living) {
            pushed = Impl.enter(maidRenderer, living, poseStack, bufferSource, packedLight,
                    partialTick);
        }
        PUSHED.get().push(pushed);
    }

    /** Closes the frame and, for a fully validated draw, defers the shared body to this point. */
    public static void exit(boolean normalReturn) {
        ArrayDeque<Boolean> stack = PUSHED.get();
        boolean pushed = !stack.isEmpty() && stack.pop();
        if (stack.isEmpty()) {
            PUSHED.remove();
        }
        if (pushed && isAvailable()) {
            Impl.exit(normalReturn);
        }
    }

    /**
     * Called at HEAD of the pinned native mesh utility. Returns true when the native full draw must
     * be cancelled because the head quads were replayed (or this was a validated body-only pass).
     */
    public static boolean capturePass(VertexConsumer output, PoseStack.Pose pose, Object metadata,
                                      float[] boneParameters, float[] auxiliary, int textureSelector,
                                      int mode, int packedLight, int packedOverlay, float red,
                                      float green, float blue, float alpha) {
        return isAvailable() && Impl.capturePass(output, pose, metadata, boneParameters, auxiliary,
                textureSelector, mode, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private static final class Impl {
        private static final String META_CLASS = "com.elfmcys.yesstevemodel.O0OOo0OoOoo0o0o0oO000o00";
        private static final String DRAW = "oOo0OO0O0o000OO0O000oo0o";
        private static final String WRAPPER_MODEL = "O00OOOo00Oo0OO0000oOo0oo";
        private static final String GEO_ENTITY = "getGeoEntity";
        private static final String META = "OOo0o0000Ooo0o00OO0oOOoO";
        private static final String BONES = "oOo0OO0O0o000OO0O000oo0o";
        private static final String AUX = "oOoo00O0o0oO0o0oO00OO0O0";
        private static final String BONE_MAP = "OO000o0ooOooooOOOOO0Ooo0";
        private static final String ALL_HEAD = "OOO0oooOOo00OOooo0OooOOo";
        private static final String HEAD_CHAIN = "oO0O000o0oooOOO0O0oooOO0";
        private static final String META_DEFS = "oOo0OO0O0o000OO0O000oo0o";
        private static final String DEF_NAME = "oOo0OO0O0o000OO0O000oo0o";
        private static final String DEF_HASH = "oOoo00O0o0oO0o0oO00OO0O0";
        private static final String YSM_FIELD = "ysmMaidRenderer";

        private static final int FLAG_COLOR = 1;
        private static final int FLAG_UV = 2;
        private static final int FLAG_UV1 = 4;
        private static final int FLAG_UV2 = 8;
        private static final int FLAG_NORMAL = 16;
        private static final int MANDATORY_FLAGS =
                FLAG_COLOR | FLAG_UV | FLAG_UV1 | FLAG_UV2 | FLAG_NORMAL;
        private static final String[] POSE_BONES = {
                "Body", "RightArm", "LeftArm", "RightLeg", "LeftLeg"
        };

        private static final boolean SUPPORTED;
        private static final Method DRAW_METHOD;
        private static final Class<?> META_TYPE;
        private static final ThreadLocal<Boolean> IN_CAPTURE = ThreadLocal.withInitial(() -> false);
        private static final ThreadLocal<ArrayDeque<Frame>> FRAMES =
                ThreadLocal.withInitial(ArrayDeque::new);
        private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
        private static final Map<Class<?>, BoneRef> BONE_REFS = new ConcurrentHashMap<>();
        private static final Map<Class<?>, Method> MAP_GETS = new ConcurrentHashMap<>();
        private static final Map<Class<?>, Method> GEO_GETS = new ConcurrentHashMap<>();
        private static final Map<Class<?>, Method> WRAPPER_MODELS = new ConcurrentHashMap<>();
        private static final Map<Class<?>, Field> YSM_FIELDS = new ConcurrentHashMap<>();
        private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

        static {
            boolean supported = false;
            Method draw = null;
            Class<?> meta = null;
            try {
                Class<?> util = Class.forName(UTIL_CLASS);
                meta = Class.forName(META_CLASS);
                draw = util.getDeclaredMethod(DRAW, VertexConsumer.class, PoseStack.Pose.class,
                        meta, float[].class, float[].class, int.class, int.class, int.class,
                        int.class, float.class, float.class, float.class, float.class);
                draw.setAccessible(true);
                supported = true;
            } catch (ReflectiveOperationException | RuntimeException t) {
                logOnce("init", "YSM 2.6.5 native bridge unavailable: " + t);
            }
            SUPPORTED = supported;
            DRAW_METHOD = draw;
            META_TYPE = meta;
        }

        private Impl() {
        }

        // ---------------------------------------------------------------- frame lifecycle

        static boolean enter(Object maidRenderer, LivingEntity entity, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, float partialTick) {
            if (!SUPPORTED) {
                return false;
            }
            try {
                if (!PenguinEntitySupport.isMaid(entity.getType())
                        || !PenguinRenderSupport.shouldReplace(entity)
                        || PenguinDeathClient.rendering(entity)) {
                    return false;
                }
                Object ysm = readYsmRenderer(maidRenderer);
                if (ysm == null) {
                    return false;
                }
                Object wrapper = invoke(ysm, geoTarget(ysm.getClass(), entity), entity);
                if (wrapper == null) {
                    return false;
                }
                Object model = resolveModel(wrapper);
                if (model == null) {
                    // The native renderer has not initialised this maid's YSM model yet. Keep the
                    // complete native draw instead of advancing the animation ourselves.
                    return false;
                }
                Frame frame = new Frame(entity, wrapper, model, poseStack, bufferSource, packedLight);
                if (!bind(frame)) {
                    return false;
                }
                FRAMES.get().push(frame);
                PenguinRenderContext.push(entity, frame.view, partialTick);
                return true;
            } catch (RuntimeException t) {
                logOnce("enter", "YSM frame enter failed: " + t);
                return false;
            }
        }

        static void exit(boolean normalReturn) {
            ArrayDeque<Frame> frames = FRAMES.get();
            Frame frame = frames.poll();
            if (frame == null) {
                return;
            }
            try {
                if (normalReturn && !frame.aborted && frame.captured && frame.bodyPose != null) {
                    drawDeferredBody(frame);
                }
            } finally {
                // The wrapper context is popped exactly once, on success and on every failure path.
                // A failed body draw surfaces instead of leaving a head-only picture behind.
                PenguinRenderContext.pop();
                if (frames.isEmpty()) {
                    FRAMES.remove();
                }
            }
        }

        /**
         * Draws the shared penguin body once, after the native geoRender delegation returned. The
         * original native modelview/pose is reproduced in a fresh PoseStack, so the native
         * MultiBufferSource builders are only flushed after their own draw has finished.
         */
        private static void drawDeferredBody(Frame frame) {
            if (frame.poseStack == null || frame.bufferSource == null) {
                return;
            }
            PoseStack fresh = new PoseStack();
            fresh.last().pose().set(frame.bodyPose);
            fresh.last().normal().set(frame.bodyNormal);
            fresh.pushPose();
            try {
                // Native pose already contains the external model/maid scale exactly once.
                fresh.scale(-1.0F, -1.0F, 1.0F);
                fresh.translate(0.0F, -1.5F, 0.0F);
                PenguinBodyLayer.renderCanonicalBody(fresh, frame.bufferSource, frame.packedLight,
                        frame.entity, frame.view);
            } finally {
                fresh.popPose();
            }
        }

        // ---------------------------------------------------------------- native mesh capture

        static boolean capturePass(VertexConsumer output, PoseStack.Pose pose, Object metadata,
                                   float[] boneParameters, float[] auxiliary, int textureSelector,
                                   int mode, int packedLight, int packedOverlay, float red,
                                   float green, float blue, float alpha) {
            if (!SUPPORTED || IN_CAPTURE.get()) {
                return false;
            }
            Frame frame = FRAMES.get().peek();
            if (frame == null || frame.aborted) {
                return false;
            }
            // Mode 0 is the ordinary third-person model. First-person hands (1/2) and custom
            // first-person models (3) must never be treated as the maid body.
            if (mode != 0) {
                return false;
            }
            if (!bind(frame)) {
                return false;
            }
            // Identity guard: another helper/object/shadow pass must stay completely native.
            if (metadata != frame.metadata || boneParameters != frame.bones
                    || auxiliary != frame.aux) {
                return false;
            }

            // The identity guard above proves these are the exact live native arrays. Their
            // backing length cannot change, and the visibility flags are part of the array, so a
            // plain exact restore is complete; no reflective flag write is needed afterwards.
            float[] savedBones = boneParameters.clone();
            float[] savedAux = auxiliary.clone();

            PoseStack identity = new PoseStack();
            PoseStack.Pose localPose = identity.last();
            CaptureConsumer full = new CaptureConsumer();
            CaptureConsumer body = new CaptureConsumer();
            boolean captured = false;
            IN_CAPTURE.set(true);
            try {
                invoke(null, DRAW_METHOD, full, localPose, frame.metadata, boneParameters,
                        auxiliary, textureSelector, mode, packedLight, packedOverlay, red, green,
                        blue, alpha);
                // Hide AllHead recursively for the second pass; the difference is the exact
                // head/hair quad set, never a guessed bone name list.
                setHidden(frame, true, true);
                invoke(null, DRAW_METHOD, body, localPose, frame.metadata, boneParameters,
                        auxiliary, textureSelector, mode, packedLight, packedOverlay, red, green,
                        blue, alpha);
                // Native animation preparation has already populated the live bone values: refresh
                // the head locator now, before the frame becomes ready and before any consumer sees
                // the deferred body.
                frame.headMatrix = headMatrix(frame.chain,
                        PenguinProportions.maid(frame.restNeckY).headDropBlocks());
                captured = true;
            } catch (RuntimeException t) {
                abort(frame, "native-capture", t);
            } finally {
                IN_CAPTURE.remove();
                // Both full arrays, including visibility flags, are restored on every path.
                System.arraycopy(savedBones, 0, boneParameters, 0, savedBones.length);
                System.arraycopy(savedAux, 0, auxiliary, 0, savedAux.length);
            }
            if (!captured || frame.aborted) {
                // No vertex has been written yet: the caller's original native full draw stays.
                return false;
            }

            List<Vertex> fullVertices = full.finish();
            List<Vertex> bodyVertices = body.finish();
            if (!full.complete || !body.complete || fullVertices.isEmpty()
                    || fullVertices.size() % 4 != 0 || bodyVertices.size() % 4 != 0) {
                abort(frame, "quad-shape", null);
                return false;
            }
            List<Quad> fullQuads = toQuads(fullVertices);
            List<Quad> bodyQuads = toQuads(bodyVertices);
            if (fullQuads == null || bodyQuads == null) {
                abort(frame, "non-finite", null);
                return false;
            }
            List<Quad> headQuads = subtract(fullQuads, bodyQuads);
            if (headQuads == null) {
                abort(frame, "body-not-subset", null);
                return false;
            }

            Matrix4f nativePose = new Matrix4f(pose.pose());
            if (frame.bodyPose == null) {
                frame.bodyPose = nativePose;
                frame.bodyNormal = new Matrix3f(pose.normal());
            }
            frame.captured = true;
            if (headQuads.isEmpty()) {
                // A body-only material pass is legitimate. Skip the native body pass instead of
                // replaying the full mesh; other passes still provide the native head.
                return true;
            }

            // Replay the already CPU-skinned native head quads into the real consumer. The
            // canonical head movement is conjugated into native Y-up exactly like the head-gear
            // layer: C * Delta * Tdrop * C^-1, then the native modelview.
            PoseStack replay = new PoseStack();
            replay.mulPose(nativePose);
            PenguinRenderContext.applyGeoHead(replay);
            Matrix4f transform = new Matrix4f(replay.last().pose());
            Matrix3f normalMatrix = new Matrix3f(transform).invert().transpose();
            for (Quad quad : headQuads) {
                write(output, quad.a(), transform, normalMatrix);
                write(output, quad.b(), transform, normalMatrix);
                write(output, quad.c(), transform, normalMatrix);
                write(output, quad.d(), transform, normalMatrix);
            }
            return true;
        }

        private static void write(VertexConsumer output, Vertex vertex, Matrix4f transform,
                                  Matrix3f normalMatrix) {
            Vector3f position = transform.transformPosition(
                    new Vector3f(vertex.x, vertex.y, vertex.z));
            output.addVertex(position.x, position.y, position.z);
            if ((vertex.flags & FLAG_COLOR) != 0) {
                int color = vertex.color;
                output.setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF,
                        (color >>> 24) & 0xFF);
            }
            if ((vertex.flags & FLAG_UV) != 0) {
                output.setUv(vertex.u, vertex.v);
            }
            if ((vertex.flags & FLAG_UV1) != 0) {
                output.setUv1(vertex.overlayLow, vertex.overlayHigh);
            }
            if ((vertex.flags & FLAG_UV2) != 0) {
                output.setUv2(vertex.lightLow, vertex.lightHigh);
            }
            if ((vertex.flags & FLAG_NORMAL) != 0) {
                // Proper normal matrix: inverse-transpose of the replay transform's upper 3x3.
                Vector3f normal = normalMatrix.transform(
                        new Vector3f(vertex.nx, vertex.ny, vertex.nz));
                if (normal.lengthSquared() > 1.0E-12F) {
                    normal.normalize();
                }
                output.setNormal(normal.x, normal.y, normal.z);
            }
        }

        private static List<Quad> toQuads(List<Vertex> vertices) {
            List<Quad> quads = new ArrayList<>(vertices.size() / 4);
            for (int i = 0; i < vertices.size(); i += 4) {
                Quad quad = new Quad(vertices.get(i), vertices.get(i + 1), vertices.get(i + 2),
                        vertices.get(i + 3));
                if (!quad.finite()) {
                    return null;
                }
                quads.add(quad);
            }
            return quads;
        }

        /**
         * Multiset subtraction that preserves the full draw order and every surviving duplicate.
         * Returns null when any body quad does not exist in the full output (proof failure).
         */
        private static List<Quad> subtract(List<Quad> full, List<Quad> body) {
            Map<Quad, Integer> remaining = new HashMap<>();
            for (Quad quad : full) {
                remaining.merge(quad, 1, Integer::sum);
            }
            for (Quad quad : body) {
                Integer count = remaining.get(quad);
                if (count == null || count <= 0) {
                    return null;
                }
                if (count == 1) {
                    remaining.remove(quad);
                } else {
                    remaining.put(quad, count - 1);
                }
            }
            List<Quad> head = new ArrayList<>();
            for (Quad quad : full) {
                Integer count = remaining.get(quad);
                if (count != null && count > 0) {
                    head.add(quad);
                    if (count == 1) {
                        remaining.remove(quad);
                    } else {
                        remaining.put(quad, count - 1);
                    }
                }
            }
            return head;
        }

        // ---------------------------------------------------------------- model binding

        private static boolean bind(Frame frame) {
            if (frame.bound) {
                return true;
            }
            try {
                Object metadata = invoke(frame.model, cached(frame.model.getClass(), META));
                float[] bones = (float[]) invoke(frame.model, cached(frame.model.getClass(), BONES));
                float[] aux = (float[]) invoke(frame.model, cached(frame.model.getClass(), AUX));
                Object allHead = invoke(frame.model, cached(frame.model.getClass(), ALL_HEAD));
                if (metadata == null || bones == null || aux == null || allHead == null) {
                    abort(frame, "model-contract", null);
                    return false;
                }
                Object chainObject = invoke(frame.model,
                        cached(frame.model.getClass(), HEAD_CHAIN));
                List<?> chain = asChain(chainObject);
                if (chain == null || chain.isEmpty()) {
                    abort(frame, "head-chain", null);
                    return false;
                }
                frame.metadata = metadata;
                frame.bones = bones;
                frame.aux = aux;
                frame.allHead = allHead;
                frame.chain = chain;
                frame.restNeckY = restNeckY(chain);
                if (Float.isNaN(frame.restNeckY)) {
                    abort(frame, "rest-neck", null);
                    return false;
                }
                // Resolve every optional reflection entry point now, before any geometry is
                // emitted. Later pose/head work must never meet a new lookup failure.
                frame.hide = find(allHead.getClass(), DRAW, boolean.class, boolean.class);
                frame.buildBoneMap();
                for (Object bone : chain) {
                    BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
                }
                for (String name : POSE_BONES) {
                    Object bone = frame.bonesByName.get(name);
                    if (bone != null) {
                        // An absent limb is the explicit neutral-pose contract; a present bone
                        // with a broken accessor is a version/API failure and aborts here.
                        BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
                    }
                }
                frame.bound = true;
                return true;
            } catch (ReflectiveOperationException | RuntimeException t) {
                abort(frame, "bind", t);
                return false;
            }
        }

        private static List<?> asChain(Object value) {
            if (value instanceof List<?> list) {
                return list;
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> list = new ArrayList<>();
                for (Object item : iterable) {
                    list.add(item);
                }
                return list;
            }
            return null;
        }

        private static float restNeckY(List<?> chain) {
            Object head = chain.get(chain.size() - 1);
            float[] point = pivot(head);
            for (int i = chain.size() - 2; i >= 0; i--) {
                Object parent = chain.get(i);
                float[] pivot = pivot(parent);
                Vector3f initial = initialRotation(parent);
                Vector3f offset = new Vector3f(point[0] - pivot[0], point[1] - pivot[1],
                        point[2] - pivot[2]);
                new Quaternionf().rotateZYX(initial.z, initial.y, initial.x).transform(offset);
                point = new float[]{offset.x + pivot[0], offset.y + pivot[1], offset.z + pivot[2]};
            }
            if (!Float.isFinite(point[1])) {
                return Float.NaN;
            }
            // Native Y-up floor 0 -> canonical Y-down floor 24 (pixels).
            return 24.0F - point[1];
        }

        /**
         * Live head locator in native block space from the current native animation chain,
         * converted to the canonical head contract {@code Tdrop * C^-1 * locator * R}. Each
         * ancestor applies its current position, pivot, ZYX rotation and scale, then removes the
         * pivot again; the final Head keeps its pivot because it is the locator endpoint. The
         * orientation basis R maps native Y-up local axes onto the canonical frame.
         */
        private static Matrix4f headMatrix(List<?> chain, float headDropBlocks) {
            PoseStack stack = new PoseStack();
            int last = chain.size() - 1;
            for (int i = 0; i < chain.size(); i++) {
                Object bone = chain.get(i);
                BoneRef ref = BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
                float px = boneFloat(ref.posX, bone);
                float py = boneFloat(ref.posY, bone);
                float pz = boneFloat(ref.posZ, bone);
                float pivotX = boneFloat(ref.pivotX, bone);
                float pivotY = boneFloat(ref.pivotY, bone);
                float pivotZ = boneFloat(ref.pivotZ, bone);
                float rotX = boneFloat(ref.rotX, bone);
                float rotY = boneFloat(ref.rotY, bone);
                float rotZ = boneFloat(ref.rotZ, bone);
                float scaleX = boneFloat(ref.scaleX, bone);
                float scaleY = boneFloat(ref.scaleY, bone);
                float scaleZ = boneFloat(ref.scaleZ, bone);
                stack.translate(-px / 16.0F, py / 16.0F, pz / 16.0F);
                stack.translate(pivotX / 16.0F, pivotY / 16.0F, pivotZ / 16.0F);
                stack.mulPose(new Quaternionf().rotateZYX(rotZ, rotY, rotX));
                stack.scale(scaleX, scaleY, scaleZ);
                if (i != last) {
                    stack.translate(-pivotX / 16.0F, -pivotY / 16.0F, -pivotZ / 16.0F);
                }
            }
            Matrix4f locator = new Matrix4f(stack.last().pose());
            Vector3f translation = locator.getTranslation(new Vector3f());
            Quaternionf rotation = locator.getUnnormalizedRotation(new Quaternionf());
            if (rotation.lengthSquared() < 1.0E-8F) {
                rotation.identity();
            } else {
                rotation.normalize();
            }
            Matrix4f rigid = new Matrix4f().translation(translation).rotate(rotation);
            Matrix4f inverseC = new Matrix4f().translate(0.0F, 1.5F, 0.0F)
                    .scale(-1.0F, -1.0F, 1.0F);
            return new Matrix4f().translate(0.0F, headDropBlocks, 0.0F).mul(inverseC).mul(rigid)
                    .mul(new Matrix4f().scale(-1.0F, -1.0F, 1.0F));
        }

        private static void applyPose(PenguinBodyModel penguin, EntityModel<?> original,
                                      LivingEntity entity) {
            if (!(original instanceof YsmView view) || !view.frame.bound) {
                return;
            }
            Frame frame = view.frame;
            // Mapping follows the coordinate chain, not the Bedrock bone name. The runtime YSM
            // default model reports native RightArm pivotX=+3.4, LeftArm=-3.4, RightLeg=+2.4,
            // LeftLeg=-2.4; C flips native X, so RightArm lands on canonical leftWing
            // (-5.45, anatomical right) and LeftArm on rightWing (+5.45, anatomical left).
            // Legs use the same side mapping. The original card's absolute rotations, native
            // capture, head and all other rig parts are unchanged.
            copy(frame, "Body", penguin.body());
            copy(frame, "RightArm", penguin.leftWing());
            copy(frame, "LeftArm", penguin.rightWing());
            copy(frame, "RightLeg", penguin.leftFoot());
            copy(frame, "LeftLeg", penguin.rightFoot());
        }

        private static void copy(Frame frame, String name, ModelPart target) {
            Object bone = frame.bone(name);
            if (bone == null || target == null) {
                // A genuinely absent native limb keeps the neutral penguin bind pose by contract.
                return;
            }
            BoneRef ref = BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
            // All accessors were resolved during bind; an invocation failure surfaces instead of
            // leaving a partially copied pose that could be drawn.
            PartPose initial = target.getInitialPose();
            float px = boneFloat(ref.posX, bone);
            float py = boneFloat(ref.posY, bone);
            float pz = boneFloat(ref.posZ, bone);
            // Same native Y-up -> canonical Y-down conversion as the Bedrock/Gecko adapters.
            target.x = initial.x + px;
            target.y = initial.y - py;
            target.z = initial.z + pz;
            target.xRot = -boneFloat(ref.rotX, bone);
            target.yRot = -boneFloat(ref.rotY, bone);
            target.zRot = boneFloat(ref.rotZ, bone);
            target.xScale = boneFloat(ref.scaleX, bone);
            target.yScale = boneFloat(ref.scaleY, bone);
            target.zScale = boneFloat(ref.scaleZ, bone);
            target.visible = !((Boolean) invoke(bone, ref.hiddenSelf));
        }

        // ---------------------------------------------------------------- reflection helpers

        private static Object resolveModel(Object wrapper) {
            Method method = WRAPPER_MODELS.computeIfAbsent(wrapper.getClass(), c -> {
                try {
                    return find(c, WRAPPER_MODEL);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            return invoke(wrapper, method);
        }

        private static Method geoTarget(Class<?> owner, LivingEntity entity) {
            return GEO_GETS.computeIfAbsent(owner, c -> {
                try {
                    return find(c, GEO_ENTITY, net.minecraft.world.entity.Entity.class);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        private static Object readYsmRenderer(Object maidRenderer) {
            Field field = YSM_FIELDS.computeIfAbsent(maidRenderer.getClass(), c -> {
                try {
                    Field f = c.getDeclaredField(YSM_FIELD);
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            try {
                return field.get(maidRenderer);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Object mapGet(Object map, int hash) {
            Method method = MAP_GETS.computeIfAbsent(map.getClass(), YsmCompatClient.Impl::findMapGet);
            return invoke(map, method, hash);
        }

        private static Method findMapGet(Class<?> type) {
            for (Class<?> iface : type.getInterfaces()) {
                try {
                    Method m = iface.getMethod("get", int.class);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                    // Keep walking the interface graph.
                }
            }
            try {
                Method m = type.getMethod("get", int.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Method cached(Class<?> owner, String name) throws ReflectiveOperationException {
            String key = owner.getName() + '#' + name;
            Method method = METHODS.get(key);
            if (method == null) {
                method = find(owner, name);
                METHODS.put(key, method);
            }
            return method;
        }

        private static Method find(Class<?> owner, String name, Class<?>... params)
                throws ReflectiveOperationException {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Method method = type.getDeclaredMethod(name, params);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    // Continue up the class hierarchy.
                }
            }
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        }

        private static Object invoke(Object target, Method method, Object... args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    // Native/programming errors are never converted into a compatibility fallback.
                    throw error;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static float boneFloat(Method getter, Object bone) {
            return ((Number) invoke(bone, getter)).floatValue();
        }

        private static void setHidden(Frame frame, boolean self, boolean children) {
            invoke(frame.allHead, frame.hide, self, children);
        }

        private static float[] pivot(Object bone) {
            BoneRef ref = BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
            return new float[]{boneFloat(ref.pivotX, bone), boneFloat(ref.pivotY, bone),
                    boneFloat(ref.pivotZ, bone)};
        }

        private static Vector3f initialRotation(Object bone) {
            BoneRef ref = BONE_REFS.computeIfAbsent(bone.getClass(), BoneRef::new);
            Vector3f rotation = (Vector3f) invoke(bone, ref.initialRotation);
            // The returned Vector3f is a live native value; never mutate it.
            return new Vector3f(rotation);
        }

        private static void abort(Frame frame, String reason, Throwable cause) {
            frame.aborted = true;
            logOnce(reason, "YSM native head capture aborted (" + reason + ")"
                    + (cause == null ? "" : ": " + cause));
        }

        private static void logOnce(String key, String message) {
            if (LOGGED.size() < 32 && LOGGED.add(key)) {
                LOGGER.warn(message);
            }
        }

        /** Cached native bone interface accessors. */
        private static final class BoneRef {
            final Method rotX;
            final Method rotY;
            final Method rotZ;
            final Method posX;
            final Method posY;
            final Method posZ;
            final Method scaleX;
            final Method scaleY;
            final Method scaleZ;
            final Method pivotX;
            final Method pivotY;
            final Method pivotZ;
            final Method initialRotation;
            final Method hiddenSelf;

            BoneRef(Class<?> type) {
                try {
                    this.rotX = find(type, "oOo0OO0O0o000OO0O000oo0o");
                    this.rotY = find(type, "oOoo00O0o0oO0o0oO00OO0O0");
                    this.rotZ = find(type, "OO000o0ooOooooOOOOO0Ooo0");
                    this.posX = find(type, "OOo0o0000Ooo0o00OO0oOOoO");
                    this.posY = find(type, "Oo0O0OoOo0O0oOoo0000O0oO");
                    this.posZ = find(type, "O0o0OoOOooOo0O0OOoo0Oo00");
                    this.scaleX = find(type, "OoooO0OO0000O00oo0Oo00OO");
                    this.scaleY = find(type, "oOOO00ooO0oOOoOOo0OoOOOo");
                    this.scaleZ = find(type, "ooOooOO0oO00o00o0o0oOOoO");
                    this.pivotX = find(type, "OOo0O00Ooo00O0Ooo0OoOo0o");
                    this.pivotY = find(type, "OO0Oo0O00OOOo0oOo0oooooO");
                    this.pivotZ = find(type, "O0O0Oo0Oooo0OOoOOO0ooo0O");
                    this.initialRotation = find(type, "O0OO0O0o00o0o00oOoO0o0oO");
                    this.hiddenSelf = find(type, "OoOO0o0O00o00OoOO0OO0OOo");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        /** Per-frame render state; bounded to one native entity draw. */
        private static final class Frame {
            final LivingEntity entity;
            final Object wrapper;
            final EntityModel<?> view;
            final PoseStack poseStack;
            final MultiBufferSource bufferSource;
            final int packedLight;
            final YsmView ysmView;

            Object model;
            Object metadata;
            float[] bones;
            float[] aux;
            Object allHead;
            List<?> chain;
            float restNeckY = Float.NaN;
            Matrix4f headMatrix;
            Method hide;
            boolean bound;
            boolean captured;
            boolean aborted;
            Matrix4f bodyPose;
            Matrix3f bodyNormal;
            Map<String, Object> bonesByName;

            Frame(LivingEntity entity, Object wrapper, Object model, PoseStack poseStack,
                  MultiBufferSource bufferSource, int packedLight) {
                this.entity = entity;
                this.wrapper = wrapper;
                this.model = model;
                this.poseStack = poseStack;
                this.bufferSource = bufferSource;
                this.packedLight = packedLight;
                this.ysmView = new YsmView(this);
                this.view = this.ysmView;
            }

            PenguinProportions.Profile profile() {
                return bound ? PenguinProportions.maid(restNeckY) : null;
            }

            /**
             * Resolves the complete name->bone map once during bind. Failure aborts the frame
             * before any geometry; a successful map is never partially replaced later.
             */
            void buildBoneMap() throws ReflectiveOperationException {
                Map<String, Object> out = new HashMap<>();
                List<?> defs = (List<?>) invoke(metadata, cached(metadata.getClass(), META_DEFS));
                Object map = invoke(model, cached(model.getClass(), BONE_MAP));
                if (defs == null || map == null) {
                    throw new IllegalStateException("YSM native bone map missing");
                }
                for (Object def : defs) {
                    String boneName = (String) invoke(def, cached(def.getClass(), DEF_NAME));
                    int hash = ((Number) invoke(def,
                            cached(def.getClass(), DEF_HASH))).intValue();
                    Object bone = mapGet(map, hash);
                    if (boneName != null && bone != null) {
                        out.put(boneName, bone);
                    }
                }
                bonesByName = out;
            }

            Object bone(String name) {
                return bonesByName == null ? null : bonesByName.get(name);
            }
        }

        /**
         * Lightweight EntityModel proxy so every existing profile/pose/head helper keeps working.
         * Ready stays false until the native quad proof succeeded for the current draw.
         */
        private static final class YsmView extends EntityModel<LivingEntity> implements GeoMaidView {
            private final Frame frame;

            YsmView(Frame frame) {
                this.frame = frame;
            }

            @Override
            public boolean kusoPenguin$ready() {
                return frame.captured && frame.bound && !frame.aborted && frame.profile() != null;
            }

            @Override
            public PenguinProportions.Profile kusoPenguin$profile() {
                return frame.profile();
            }

            @Override
            public PenguinPoseAdapter kusoPenguin$adapter() {
                return frame.bound ? Impl::applyPose : null;
            }

            @Override
            public Matrix4f kusoPenguin$headMatrix() {
                return frame.headMatrix == null ? new Matrix4f() : new Matrix4f(frame.headMatrix);
            }

            @Override
            public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw, float headPitch) {
                // The native YSM animation state already owns this entity.
            }

            @Override
            public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                       int packedOverlay, int color) {
                // Never drawn standalone; the native geoRender body pass owns the placement.
            }
        }

        // ---------------------------------------------------------------- vertex data

        private static final class Vertex {
            final float x;
            final float y;
            final float z;
            final int color;
            final float u;
            final float v;
            final int overlayLow;
            final int overlayHigh;
            final int lightLow;
            final int lightHigh;
            final float nx;
            final float ny;
            final float nz;
            final int flags;

            Vertex(float x, float y, float z, int color, float u, float v, int overlayLow,
                   int overlayHigh, int lightLow, int lightHigh, float nx, float ny, float nz,
                   int flags) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.color = color;
                this.u = u;
                this.v = v;
                this.overlayLow = overlayLow;
                this.overlayHigh = overlayHigh;
                this.lightLow = lightLow;
                this.lightHigh = lightHigh;
                this.nx = nx;
                this.ny = ny;
                this.nz = nz;
                this.flags = flags;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof Vertex vertex)) {
                    return false;
                }
                return Float.floatToIntBits(x) == Float.floatToIntBits(vertex.x)
                        && Float.floatToIntBits(y) == Float.floatToIntBits(vertex.y)
                        && Float.floatToIntBits(z) == Float.floatToIntBits(vertex.z)
                        && color == vertex.color
                        && Float.floatToIntBits(u) == Float.floatToIntBits(vertex.u)
                        && Float.floatToIntBits(v) == Float.floatToIntBits(vertex.v)
                        && overlayLow == vertex.overlayLow && overlayHigh == vertex.overlayHigh
                        && lightLow == vertex.lightLow && lightHigh == vertex.lightHigh
                        && Float.floatToIntBits(nx) == Float.floatToIntBits(vertex.nx)
                        && Float.floatToIntBits(ny) == Float.floatToIntBits(vertex.ny)
                        && Float.floatToIntBits(nz) == Float.floatToIntBits(vertex.nz)
                        && flags == vertex.flags;
            }

            @Override
            public int hashCode() {
                int result = Float.floatToIntBits(x);
                result = 31 * result + Float.floatToIntBits(y);
                result = 31 * result + Float.floatToIntBits(z);
                result = 31 * result + color;
                result = 31 * result + Float.floatToIntBits(u);
                result = 31 * result + Float.floatToIntBits(v);
                result = 31 * result + overlayLow;
                result = 31 * result + overlayHigh;
                result = 31 * result + lightLow;
                result = 31 * result + lightHigh;
                result = 31 * result + Float.floatToIntBits(nx);
                result = 31 * result + Float.floatToIntBits(ny);
                result = 31 * result + Float.floatToIntBits(nz);
                result = 31 * result + flags;
                return result;
            }
        }

        private record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
            boolean finite() {
                return Float.isFinite(a.x) && Float.isFinite(a.y) && Float.isFinite(a.z)
                        && Float.isFinite(b.x) && Float.isFinite(b.y) && Float.isFinite(b.z)
                        && Float.isFinite(c.x) && Float.isFinite(c.y) && Float.isFinite(c.z)
                        && Float.isFinite(d.x) && Float.isFinite(d.y) && Float.isFinite(d.z);
            }
        }

        /**
         * Records the native vertex stream. Native {@link VertexConsumer} has no endVertex, so a
         * vertex becomes complete once addVertex and its attribute setters have run. It is stored
         * on the next addVertex or, for the trailing vertex, by {@link #finish()}. A vertex that
         * lacks any mandatory native attribute (COLOR, UV, UV1, UV2, NORMAL) marks the whole pass
         * incomplete and is never stored with invented attributes.
         */
        private static final class CaptureConsumer implements VertexConsumer {
            private final List<Vertex> vertices = new ArrayList<>();
            boolean complete = true;
            private boolean started;
            private float x;
            private float y;
            private float z;
            private int color;
            private float u;
            private float v;
            private int overlayLow;
            private int overlayHigh;
            private int lightLow;
            private int lightHigh;
            private float nx;
            private float ny;
            private float nz;
            private int flags;

            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                flush();
                this.started = true;
                this.x = x;
                this.y = y;
                this.z = z;
                this.flags = 0;
                return this;
            }

            @Override
            public VertexConsumer setColor(int red, int green, int blue, int alpha) {
                this.color = (alpha << 24) | (red << 16) | (green << 8) | blue;
                this.flags |= FLAG_COLOR;
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                this.u = u;
                this.v = v;
                this.flags |= FLAG_UV;
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                this.overlayLow = u;
                this.overlayHigh = v;
                this.flags |= FLAG_UV1;
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                this.lightLow = u;
                this.lightHigh = v;
                this.flags |= FLAG_UV2;
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                this.nx = x;
                this.ny = y;
                this.nz = z;
                this.flags |= FLAG_NORMAL;
                return this;
            }

            List<Vertex> finish() {
                // Finalisation protocol: the last native vertex is only pending because no
                // subsequent addVertex arrived. Flush it exactly like the next addVertex would,
                // with the same completeness validation; never drop it and never invent values.
                flush();
                return vertices;
            }

            private void flush() {
                if (!started) {
                    return;
                }
                started = false;
                if ((flags & MANDATORY_FLAGS) != MANDATORY_FLAGS) {
                    // Missing native attributes are a real data failure, not a flush artefact.
                    complete = false;
                    return;
                }
                vertices.add(new Vertex(x, y, z, color, u, v, overlayLow, overlayHigh, lightLow,
                        lightHigh, nx, ny, nz, flags));
            }
        }
    }
}
