package com.luochun.kusopenguin.dance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinRig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure-Java, bounded importer for canonical dance JSON and a deliberately small Mixamo COLLADA
 * subset. It never launches Python/Blender and never accepts XML DTDs or external entities.
 *
 * <p>The DAE path reads the visual-scene node hierarchy and its rest matrices, requires one
 * synchronous LINEAR {@code node/matrix} channel per bone needed by the fixed 16-joint penguin
 * rig, and transfers source world orientations and limb directions with the existing
 * {@code C(v)=-v} Mixamo convention. No artistic joint clamps are applied: only finite/unit
 * validation remains. Root translation is never copied; the source's vertical hips excursion is
 * scaled anatomically into the pelvis sample inside the 24-pixel translation contract. The
 * finished JSON is passed through {@link DanceLibrary#canonicalize(byte[])} so the server and
 * client share exactly one schema/bind validator.</p>
 */
public final class DanceImport {
    public static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;
    public static final int MAX_XML_DEPTH = 64;
    public static final int MAX_XML_NODES = 200_000;
    public static final long MAX_XML_TEXT_CHARS = 16L * 1024L * 1024L;
    public static final float SOURCE_FPS = 30.0F;
    public static final float FPS_TOLERANCE = 1.0E-3F;
    public static final float MAX_DURATION = DanceLibrary.MAX_SECONDS;

    private static final Gson GSON = new GsonBuilder().create();
    private static final String TAUNT_RESOURCE = "/assets/kuso_penguin/animations/taunt.json";
    private static volatile PenguinAnimationClip tauntClip;

    private static final String[] REQUIRED_BONES = {
        "Hips", "Spine2", "Neck", "Head",
        "RightArm", "RightForeArm", "RightHand", "RightHandMiddle1",
        "LeftArm", "LeftForeArm", "LeftHand", "LeftHandMiddle1",
        "RightUpLeg", "RightLeg", "RightFoot", "RightToeBase",
        "LeftUpLeg", "LeftLeg", "LeftFoot", "LeftToeBase"
    };

    /** Immutable import result; {@code canonical} is the exact upload payload. */
    public record Result(DanceLibrary.Canonical canonical, String format, float duration,
                         int frames, boolean rootMotionRemoved, String name) {
        public Result {
            Objects.requireNonNull(canonical, "canonical");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(name, "name");
        }
    }

    private DanceImport() {
    }

    /** Read one user-chosen file and convert it to the canonical 16-joint wire format. */
    public static Result convert(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a regular file: " + file);
        }
        long size = Files.size(file);
        if (size <= 0 || size > MAX_SOURCE_BYTES) {
            throw new IOException("Dance source must be 1 byte .. 8 MiB: " + file.getFileName());
        }
        byte[] source = Files.readAllBytes(file);
        String name = file.getFileName().toString();
        return convert(source, name);
    }

    /** Test/UI entry that does not require a filesystem path; the name is only display metadata. */
    public static Result convert(byte[] source, String fileName) throws IOException {
        if (source == null || source.length == 0) {
            throw new IOException("Dance source is empty");
        }
        if (source.length > MAX_SOURCE_BYTES) {
            throw new IOException("Dance source exceeds 8 MiB");
        }
        String text = new String(source, StandardCharsets.UTF_8);
        String lead = text.stripLeading();
        String stem = safeName(fileName);
        if (lead.startsWith("{")) {
            DanceLibrary.Canonical canonical = DanceLibrary.canonicalize(source);
            return new Result(canonical, "canonical JSON", canonical.duration(), canonical.frames(),
                true, stem);
        }
        if (lead.startsWith("<")) {
            if (!lead.startsWith("<COLLADA") && !lead.startsWith("<?xml")) {
                throw new IOException("DAE root must be COLLADA: " + fileName);
            }
            return convertDae(source, stem);
        }
        throw new IOException("Only canonical dance JSON or COLLADA DAE is supported");
    }

    private static Result convertDae(byte[] source, String name) throws IOException {
        XNode root = parseXml(source);
        if (!"COLLADA".equals(root.name)) {
            throw new IOException("DAE root element must be COLLADA");
        }
        XNode asset = child(root, "asset");
        if (asset == null) {
            throw new IOException("DAE has no asset metadata");
        }
        XNode unit = child(asset, "unit");
        if (unit == null) {
            throw new IOException("DAE is missing its asset unit");
        }
        double meter = finiteDouble(unit.attr("meter"), "asset unit meter");
        if (meter <= 0) {
            throw new IOException("DAE asset unit must be finite and positive");
        }
        String upAxis = textOf(child(asset, "up_axis"));
        if (!"Y_UP".equals(upAxis)) {
            throw new IOException("Only Y_UP DAE assets are supported");
        }

        XNode sceneLibrary = child(root, "library_visual_scenes");
        XNode scene = sceneLibrary == null ? null : child(sceneLibrary, "visual_scene");
        if (scene == null) {
            throw new IOException("DAE has no visual scene hierarchy");
        }
        Map<String, XNode> byId = new HashMap<>();
        collectIds(root, byId);
        Map<String, Bone> bones = new LinkedHashMap<>();
        for (XNode node : childrenNamed(scene, "node")) {
            collectBones(node, null, bones);
        }
        if (bones.isEmpty()) {
            throw new IOException("DAE visual scene has no JOINT nodes");
        }

        Set<String> needed = neededBones(bones);
        Animations animations = readAnimations(root, bones, needed);
        Map<String, Matrix4f[]> localChannels = animations.locals();
        float[] times = animations.times();
        if (times == null) {
            throw new IOException("DAE has no animation channels");
        }
        if (times.length < 2 || times.length > DanceLibrary.MAX_FRAMES) {
            throw new IOException("DAE frame count must be 2.." + DanceLibrary.MAX_FRAMES);
        }
        float duration = times[times.length - 1];
        if (duration <= 0 || duration > MAX_DURATION) {
            throw new IOException("DAE duration must be within 30 seconds");
        }
        for (int i = 1; i < times.length; i++) {
            float step = times[i] - times[i - 1];
            if (Math.abs(step - 1.0F / SOURCE_FPS) > FPS_TOLERANCE) {
                throw new IOException("DAE must use 30 FPS timestamps; frame " + i
                    + " step is " + step);
            }
        }
        float blendIn = 0.10F;
        float blendOut = 0.20F;
        if (duration + blendIn + blendOut > MAX_DURATION) {
            blendIn = 0.05F;
            blendOut = 0.05F;
        }
        if (duration + blendIn + blendOut > MAX_DURATION) {
            throw new IOException("DAE duration leaves no room for blend-in/out under 30 seconds");
        }

        PenguinAnimationClip reference = taunt();
        Object[] frameData = retarget(bones, localChannels, times, reference);
        byte[] canonicalBytes = buildCanonical(name, source, times, blendIn, blendOut,
            (Quaternionf[][]) frameData[0], (Vector3f[][]) frameData[1], reference);
        DanceLibrary.Canonical canonical = DanceLibrary.canonicalize(canonicalBytes);
        if (Math.abs(canonical.duration() - (duration + blendIn + blendOut)) > 0.001F) {
            throw new IOException("Retargeted duration does not match the source");
        }
        return new Result(canonical, "Mixamo DAE", duration, times.length, true, name);
    }

    private static Set<String> neededBones(Map<String, Bone> bones) throws IOException {
        Set<String> needed = new HashSet<>();
        for (String required : REQUIRED_BONES) {
            Bone bone = bones.get(required);
            if (bone == null) {
                throw new IOException("DAE is missing required Mixamo bone: " + required);
            }
            for (Bone current = bone; current != null; current = current.parent) {
                needed.add(current.name);
            }
        }
        return needed;
    }

    private record Animations(Map<String, Matrix4f[]> locals, float[] times) {
    }

    private static Animations readAnimations(XNode root, Map<String, Bone> bones,
                                              Set<String> needed) throws IOException {
        XNode library = child(root, "library_animations");
        if (library == null) {
            throw new IOException("DAE has no animation library");
        }
        Map<String, XNode> ids = new HashMap<>();
        collectIds(root, ids);
        List<XNode> animations = new ArrayList<>();
        collectAnimations(library, animations);
        Map<String, Matrix4f[]> channels = new HashMap<>();
        Map<String, String> channelTargets = new HashMap<>();
        float[] common = null;
        for (XNode animation : animations) {
            for (XNode channel : childrenNamed(animation, "channel")) {
                String target = channel.attr("target");
                if (target == null || target.isBlank()) {
                    throw new IOException("DAE channel has no target");
                }
                int slash = target.indexOf('/');
                if (slash <= 0 || slash == target.length() - 1) {
                    throw new IOException("Unsupported DAE channel target: " + target);
                }
                String boneName = normalizeBoneName(target.substring(0, slash));
                String path = target.substring(slash + 1);
                if (!"matrix".equals(path)) {
                    throw new IOException("Unsupported DAE channel path '" + path
                        + "' for bone " + boneName);
                }
                if (!bones.containsKey(boneName)) {
                    throw new IOException("DAE channel targets unknown bone: " + boneName);
                }
                if (channelTargets.putIfAbsent(boneName, path) != null) {
                    throw new IOException("DAE has duplicate matrix channels for bone: " + boneName);
                }
                XNode sampler = resolve(ids, channel.attr("source"), "channel sampler");
                float[] channelTimes = readTimes(ids, sampler, animation);
                if (common == null) {
                    common = channelTimes;
                } else {
                    if (common.length != channelTimes.length) {
                        throw new IOException("DAE animation channels have different frame counts");
                    }
                    for (int i = 0; i < common.length; i++) {
                        if (Float.floatToIntBits(common[i]) != Float.floatToIntBits(channelTimes[i])) {
                            throw new IOException("DAE animation channels have non-synchronous times"
                                + " at frame " + i);
                        }
                    }
                }
                Matrix4f[] locals = readMatrices(ids, sampler, animation, boneName, channelTimes.length);
                if (needed.contains(boneName)) {
                    channels.put(boneName, locals);
                }
            }
        }
        for (String name : needed) {
            if (!channels.containsKey(name)) {
                throw new IOException("DAE is missing an animation matrix channel for bone: " + name);
            }
        }
        return new Animations(channels, common);
    }

    private static void collectAnimations(XNode parent, List<XNode> result) {
        for (XNode child : parent.children) {
            if ("animation".equals(child.name)) {
                result.add(child);
                collectAnimations(child, result);
            }
        }
    }

    private static float[] readTimes(Map<String, XNode> ids, XNode sampler, XNode animation)
        throws IOException {
        XNode input = samplerInput(sampler, "INPUT");
        if (input == null) {
            throw new IOException("DAE sampler is missing INPUT");
        }
        XNode source = resolve(ids, input.attr("source"), "sampler INPUT");
        float[] values = readFloatSource(source, "animation input");
        if (values.length < 2 || values.length > DanceLibrary.MAX_FRAMES) {
            throw new IOException("DAE animation input count must be 2.." + DanceLibrary.MAX_FRAMES);
        }
        if (values[0] != 0.0F) {
            throw new IOException("DAE animation must start at time 0");
        }
        for (int i = 1; i < values.length; i++) {
            if (!(values[i] > values[i - 1])) {
                throw new IOException("DAE animation times must be strictly increasing");
            }
        }
        XNode interpolation = samplerInput(sampler, "INTERPOLATION");
        if (interpolation == null) {
            throw new IOException("DAE sampler is missing INTERPOLATION");
        }
        XNode interpolationSource = resolve(ids, interpolation.attr("source"),
            "sampler INTERPOLATION");
        List<String> modes = readStringSource(interpolationSource);
        if (modes.size() != values.length) {
            throw new IOException("DAE interpolation count does not match the frame count");
        }
        for (String mode : modes) {
            if (!"LINEAR".equals(mode)) {
                throw new IOException("Only LINEAR DAE matrix channels are supported, found " + mode);
            }
        }
        return values;
    }

    private static Matrix4f[] readMatrices(Map<String, XNode> ids, XNode sampler, XNode animation,
                                            String bone, int frames) throws IOException {
        XNode output = samplerInput(sampler, "OUTPUT");
        if (output == null) {
            throw new IOException("DAE sampler is missing OUTPUT for bone " + bone);
        }
        XNode source = resolve(ids, output.attr("source"), "sampler OUTPUT");
        XNode array = child(source, "float_array");
        if (array == null) {
            throw new IOException("DAE matrix source has no float_array for bone " + bone);
        }
        float[] values = parseFloats(textOf(array), "matrix source for " + bone);
        XNode accessor = child(source, "technique_common") == null ? null
            : child(child(source, "technique_common"), "accessor");
        int stride = 16;
        int count = frames;
        if (accessor != null) {
            count = finiteInt(accessor.attr("count"), "matrix accessor count for " + bone);
            stride = accessor.attr("stride") == null ? 16
                : finiteInt(accessor.attr("stride"), "matrix accessor stride for " + bone);
        }
        if (count != frames || stride != 16 || values.length != frames * 16) {
            throw new IOException("DAE matrix output for bone " + bone
                + " must contain exactly 16 values per frame");
        }
        Matrix4f[] result = new Matrix4f[frames];
        for (int frame = 0; frame < frames; frame++) {
            float[] matrix = new float[16];
            System.arraycopy(values, frame * 16, matrix, 0, 16);
            result[frame] = matrix(matrix, bone + " frame " + frame);
        }
        return result;
    }

    private static XNode samplerInput(XNode sampler, String semantic) throws IOException {
        XNode found = null;
        for (XNode input : childrenNamed(sampler, "input")) {
            if (semantic.equals(input.attr("semantic"))) {
                if (found != null) {
                    throw new IOException("DAE sampler has duplicate " + semantic + " inputs");
                }
                found = input;
            }
        }
        return found;
    }

    private static XNode resolve(Map<String, XNode> ids, String reference, String what)
        throws IOException {
        if (reference == null || !reference.startsWith("#")) {
            throw new IOException("DAE " + what + " has an invalid source reference");
        }
        XNode node = ids.get(reference.substring(1));
        if (node == null) {
            throw new IOException("DAE " + what + " references a missing source " + reference);
        }
        return node;
    }

    private static float[] readFloatSource(XNode source, String what) throws IOException {
        XNode array = child(source, "float_array");
        if (array == null) {
            throw new IOException("DAE " + what + " has no float_array");
        }
        float[] values = parseFloats(textOf(array), what);
        XNode technique = child(source, "technique_common");
        XNode accessor = technique == null ? null : child(technique, "accessor");
        if (accessor != null) {
            int count = finiteInt(accessor.attr("count"), what + " count");
            int stride = accessor.attr("stride") == null ? 1
                : finiteInt(accessor.attr("stride"), what + " stride");
            if (stride != 1) {
                throw new IOException("DAE " + what + " must have stride 1");
            }
            if (count != values.length) {
                throw new IOException("DAE " + what + " accessor count does not match its data");
            }
        }
        return values;
    }

    private static List<String> readStringSource(XNode source) throws IOException {
        XNode array = child(source, "Name_array");
        if (array == null) {
            array = child(source, "IDREF_array");
        }
        if (array == null) {
            throw new IOException("DAE interpolation source has no Name_array/IDREF_array");
        }
        List<String> values = new ArrayList<>();
        for (String token : textOf(array).trim().split("\\s+")) {
            if (!token.isEmpty()) {
                values.add(token);
            }
        }
        if (values.isEmpty()) {
            throw new IOException("DAE interpolation array is empty");
        }
        return values;
    }

    private static void collectIds(XNode node, Map<String, XNode> ids) throws IOException {
        String id = node.attr("id");
        if (id != null && !id.isBlank()) {
            if (ids.putIfAbsent(id, node) != null) {
                throw new IOException("DAE has a duplicate element id: " + id);
            }
        }
        for (XNode child : node.children) {
            collectIds(child, ids);
        }
    }

    private static void collectBones(XNode node, Bone parent, Map<String, Bone> bones)
        throws IOException {
        if (!"JOINT".equals(node.attr("type"))) {
            // The supported subset has no animated/non-neutral wrapper above the skeleton.
            // Silently skipping such a transform would import a different coordinate frame.
            for (XNode child : node.children) {
                if (Set.of("matrix", "translate", "rotate", "scale", "lookat", "skew").contains(child.name)) {
                    throw new IOException("Unsupported non-JOINT scene transform: " + child.name);
                }
            }
            for (XNode child : childrenNamed(node, "node")) {
                collectBones(child, parent, bones);
            }
            return;
        }
        String raw = node.attr("name");
        if (raw == null || raw.isBlank()) {
            raw = node.attr("sid");
        }
        if (raw == null || raw.isBlank()) {
            raw = node.attr("id");
        }
        String name = normalizeBoneName(raw);
        if (name.isEmpty()) {
            throw new IOException("DAE JOINT has no usable name");
        }
        Matrix4f local = matrixOfNode(node, name);
        Bone bone = new Bone(name, local, parent);
        if (bones.putIfAbsent(name, bone) != null) {
            throw new IOException("DAE has duplicate normalized Mixamo bone: " + name);
        }
        if (parent != null) {
            parent.children.add(bone);
        }
        for (XNode child : childrenNamed(node, "node")) {
            collectBones(child, bone, bones);
        }
    }

    private static Matrix4f matrixOfNode(XNode node, String bone) throws IOException {
        XNode matrix = null;
        for (XNode child : node.children) {
            if (Set.of("translate", "rotate", "scale", "lookat", "skew").contains(child.name)) {
                throw new IOException("DAE bone " + bone + " has an unsupported " + child.name + " transform");
            }
        }
        for (XNode child : childrenNamed(node, "matrix")) {
            if (matrix != null) {
                throw new IOException("DAE bone has multiple rest matrices: " + bone);
            }
            matrix = child;
        }
        if (matrix == null) {
            throw new IOException("DAE bone has no rest matrix: " + bone);
        }
        return matrix(parseFloats(textOf(matrix), "rest matrix for " + bone), bone + " rest");
    }

    private static Matrix4f matrix(float[] values, String what) throws IOException {
        if (values.length != 16) {
            throw new IOException("DAE " + what + " must have 16 matrix values");
        }
        // COLLADA fixture matrices are written row by row; JOML set(float[]) is column-major.
        Matrix4f matrix = new Matrix4f().set(values).transpose();
        Vector3f c0 = matrix.getColumn(0, new Vector3f());
        Vector3f c1 = matrix.getColumn(1, new Vector3f());
        Vector3f c2 = matrix.getColumn(2, new Vector3f());
        float l0 = c0.length(), l1 = c1.length(), l2 = c2.length();
        if (!finite(l0) || !finite(l1) || !finite(l2)
            || l0 < 1.0E-4F || l1 < 1.0E-4F || l2 < 1.0E-4F
            || l0 > 1.0E4F || l1 > 1.0E4F || l2 > 1.0E4F) {
            throw new IOException("DAE " + what + " has a zero or non-finite scale");
        }
        if (Math.abs(c0.dot(c1) / (l0 * l1)) > 1.0E-3F
            || Math.abs(c0.dot(c2) / (l0 * l2)) > 1.0E-3F
            || Math.abs(c1.dot(c2) / (l1 * l2)) > 1.0E-3F) {
            throw new IOException("DAE " + what + " contains shear");
        }
        float determinant = c0.cross(c1, new Vector3f()).dot(c2);
        if (!(determinant > 0)) {
            throw new IOException("DAE " + what + " contains a mirror or degenerate basis");
        }
        if (Math.abs(matrix.m03()) > 1.0E-4F || Math.abs(matrix.m13()) > 1.0E-4F
            || Math.abs(matrix.m23()) > 1.0E-4F || Math.abs(matrix.m33() - 1.0F) > 1.0E-4F) {
            throw new IOException("DAE " + what + " has an invalid perspective row");
        }
        if (!finite(matrix.m30()) || !finite(matrix.m31()) || !finite(matrix.m32())
            || Math.abs(matrix.m30()) > 1.0E6F || Math.abs(matrix.m31()) > 1.0E6F
            || Math.abs(matrix.m32()) > 1.0E6F) {
            throw new IOException("DAE " + what + " has a non-finite or excessive translation");
        }
        return matrix;
    }

    private static Object[] retarget(Map<String, Bone> bones, Map<String, Matrix4f[]> locals,
                                     float[] times, PenguinAnimationClip reference)
        throws IOException {
        Map<Bone, Matrix4f> restWorld = restWorldMatrices(bones);
        float restHipsY = restWorld.get(bones.get("Hips")).getTranslation(new Vector3f()).y;
        // Anatomical scale: the 16-pixel canonical pelvis-to-head span over the largest
        // Hips->Head extension in the clip. This is a body-length ratio, not a pose calibration.
        float torsoSpan = reference.bind(PenguinRig.HEAD)
            .sub(reference.bind(PenguinRig.PELVIS)).length();
        float maxHipsHead = 0.0F;
        for (int frame = 0; frame < times.length; frame++) {
            Map<Bone, Matrix4f> world = worldMatrices(bones, locals, frame);
            Vector3f hipsPos = world.get(bones.get("Hips")).getTranslation(new Vector3f());
            Vector3f headPos = world.get(bones.get("Head")).getTranslation(new Vector3f());
            maxHipsHead = Math.max(maxHipsHead, hipsPos.distance(headPos));
        }
        if (!(maxHipsHead > 1.0E-4F)) {
            throw new IOException("DAE Hips->Head span is degenerate");
        }
        float pixelsPerUnit = torsoSpan / maxHipsHead;
        Quaternionf[][] result = new Quaternionf[times.length][PenguinRig.COUNT];
        Vector3f[][] translations = new Vector3f[times.length][PenguinRig.COUNT];
        for (int frame = 0; frame < times.length; frame++) {
            Map<Bone, Matrix4f> world = worldMatrices(bones, locals, frame);
            Quaternionf hips = rotation(world.get(bones.get("Hips")));
            Quaternionf spine2 = rotation(world.get(bones.get("Spine2")));
            Quaternionf head = rotation(world.get(bones.get("Head")));
            Quaternionf[] q = result[frame];
            Vector3f[] p = translations[frame];
            for (int i = 0; i < q.length; i++) {
                q[i] = new Quaternionf();
                p[i] = new Vector3f();
            }
            // C(v)=-v transfers source world rotations unchanged (C R C^-1 = R). Use the
            // source world orientations directly; never subtract this clip's first pose as rest.
            q[1] = unit(new Quaternionf(hips), "pelvis", frame);
            q[2] = unit(new Quaternionf(hips).conjugate().mul(spine2), "chest", frame);
            q[3] = unit(new Quaternionf(spine2).conjugate().mul(head), "head", frame);
            Vector3f hipsPos = world.get(bones.get("Hips")).getTranslation(new Vector3f());
            p[1] = new Vector3f(0.0F, -(hipsPos.y - restHipsY) * pixelsPerUnit, 0.0F);
            if (!finite(p[1].y) || p[1].length() > 24.0F) {
                throw new IOException("Pelvis visual displacement exceeds 24 pixels on frame "
                    + frame);
            }
            retargetArm(q, 4, "Right", world, bones, reference, frame, spine2);
            retargetArm(q, 7, "Left", world, bones, reference, frame, spine2);
            retargetLeg(q, 10, "Right", world, bones, frame);
            retargetLeg(q, 13, "Left", world, bones, frame);
        }
        return new Object[]{result, translations};
    }

    private static void retargetArm(Quaternionf[] q, int offset, String side,
                                    Map<Bone, Matrix4f> world, Map<String, Bone> bones,
                                    PenguinAnimationClip reference, int frame,
                                    Quaternionf chestWorld) throws IOException {
        String arm = side + "Arm";
        String foreArm = side + "ForeArm";
        String hand = side + "Hand";
        String middle = side + "HandMiddle1";
        Vector3f upper = direction(world, bones, arm, foreArm, frame);
        Vector3f fore = direction(world, bones, foreArm, hand, frame);
        Vector3f handDir = direction(world, bones, hand, middle, frame);
        Vector3f b0 = reference.bind(offset);
        Vector3f b1 = reference.bind(offset + 1);
        Vector3f b2 = reference.bind(offset + 2);
        Vector3f v01 = new Vector3f(b1).sub(b0).normalize();
        Vector3f v12 = new Vector3f(b2).sub(b1).normalize();
        // The reviewed visible wing end, mirrored for the negative-X wing.
        Vector3f tip = new Vector3f(side.equals("Right") ? 0.78F : -0.78F, 2.0F, 0.17F)
            .normalize();
        Quaternionf upperWorld = aim(v01, upper.negate(), arm, frame);
        q[offset] = unit(new Quaternionf(chestWorld).conjugate().mul(upperWorld), arm, frame);
        Quaternionf foreWorld = aim(v12, fore.negate(), foreArm, frame);
        q[offset + 1] = unit(new Quaternionf(upperWorld).conjugate().mul(foreWorld),
            foreArm, frame);
        Quaternionf tipWorld = aim(tip, handDir.negate(), hand, frame);
        q[offset + 2] = unit(new Quaternionf(foreWorld).conjugate().mul(tipWorld), hand, frame);
    }

    private static void retargetLeg(Quaternionf[] q, int offset, String side,
                                    Map<Bone, Matrix4f> world, Map<String, Bone> bones, int frame)
        throws IOException {
        String upLeg = side + "UpLeg";
        String leg = side + "Leg";
        String foot = side + "Foot";
        String toe = side + "ToeBase";
        Vector3f thigh = direction(world, bones, upLeg, leg, frame);
        Vector3f calf = direction(world, bones, leg, foot, frame);
        Vector3f toes = direction(world, bones, foot, toe, frame);
        Vector3f down = new Vector3f(0.0F, 1.0F, 0.0F);
        // Canonical leg roots are children of root (identity), not the pelvis.
        Quaternionf thighWorld = aim(down, thigh.negate(), upLeg, frame);
        q[offset] = unit(new Quaternionf(thighWorld), upLeg, frame);
        Quaternionf calfWorld = aim(down, calf.negate(), leg, frame);
        q[offset + 1] = unit(new Quaternionf(thighWorld).conjugate().mul(calfWorld), leg, frame);
        Quaternionf footWorld = aim(new Vector3f(0.0F, 0.0F, -1.0F), toes.negate(), foot, frame);
        q[offset + 2] = unit(new Quaternionf(calfWorld).conjugate().mul(footWorld), foot, frame);
    }

    private static Vector3f direction(Map<Bone, Matrix4f> world, Map<String, Bone> bones,
                                      String start, String end, int frame) throws IOException {
        Bone startBone = bones.get(start);
        Bone endBone = bones.get(end);
        if (startBone == null || endBone == null) {
            throw new IOException("Missing required retarget bone on frame " + frame);
        }
        Vector3f from = world.get(startBone).getTranslation(new Vector3f());
        Vector3f to = world.get(endBone).getTranslation(new Vector3f());
        Vector3f delta = to.sub(from);
        if (delta.lengthSquared() < 1.0E-12F) {
            throw new IOException("Degenerate bone direction " + start + " -> " + end
                + " on frame " + frame);
        }
        return delta.normalize();
    }

    private static Quaternionf unit(Quaternionf q, String bone, int frame) throws IOException {
        if (!finite(q.x) || !finite(q.y) || !finite(q.z) || !finite(q.w)
            || q.lengthSquared() < 1.0E-12F) {
            throw new IOException("Non-finite or zero rotation for " + bone + " on frame " + frame);
        }
        return q.normalize();
    }

    private static Map<Bone, Matrix4f> worldMatrices(Map<String, Bone> bones,
                                                     Map<String, Matrix4f[]> locals, int frame) {
        Map<Bone, Matrix4f> result = new LinkedHashMap<>();
        for (Bone bone : bones.values()) {
            if (locals.containsKey(bone.name)) {
                world(bone, frame, locals, result);
            }
        }
        return result;
    }

    private static Map<Bone, Matrix4f> restWorldMatrices(Map<String, Bone> bones) {
        Map<Bone, Matrix4f> result = new LinkedHashMap<>();
        for (Bone bone : bones.values()) {
            restWorld(bone, result);
        }
        return result;
    }

    private static Matrix4f restWorld(Bone bone, Map<Bone, Matrix4f> memo) {
        Matrix4f cached = memo.get(bone);
        if (cached != null) {
            return cached;
        }
        Matrix4f result = bone.parent == null ? new Matrix4f(bone.restLocal)
            : new Matrix4f(restWorld(bone.parent, memo)).mul(bone.restLocal);
        memo.put(bone, result);
        return result;
    }

    private static Matrix4f world(Bone bone, int frame, Map<String, Matrix4f[]> locals,
                                  Map<Bone, Matrix4f> memo) {
        Matrix4f cached = memo.get(bone);
        if (cached != null) {
            return cached;
        }
        Matrix4f local = locals.get(bone.name)[frame];
        Matrix4f result = bone.parent == null ? new Matrix4f(local)
            : new Matrix4f(world(bone.parent, frame, locals, memo)).mul(local);
        memo.put(bone, result);
        return result;
    }

    private static Quaternionf rotation(Matrix4f matrix) {
        Quaternionf q = matrix.getUnnormalizedRotation(new Quaternionf());
        if (!finite(q.x) || !finite(q.y) || !finite(q.z) || !finite(q.w)
            || q.lengthSquared() < 1.0E-12F) {
            throw new IllegalArgumentException("Non-finite or zero matrix rotation");
        }
        return q.normalize();
    }

    private static Quaternionf aim(Vector3f from, Vector3f to, String bone, int frame)
        throws IOException {
        Vector3f a = new Vector3f(from).normalize();
        Vector3f b = new Vector3f(to).normalize();
        float dot = a.dot(b);
        if (dot < -0.999999F) {
            Vector3f axis = new Vector3f(Math.abs(a.x) < 0.9F ? 1 : 0, 0, Math.abs(a.x) < 0.9F ? 0 : 1)
                .cross(a).normalize();
            return new Quaternionf(axis.x, axis.y, axis.z, 0);
        }
        // cross is the XYZ part, and 1+dot is W. Normalize the FOUR components together.
        // Parallel directions correctly yield (0,0,0,1), not a spurious diagonal rotation.
        Vector3f cross = a.cross(b, new Vector3f());
        return new Quaternionf(cross.x, cross.y, cross.z, 1.0F + dot).normalize();
    }

    private static byte[] buildCanonical(String name, byte[] source, float[] times,
                                         float blendIn, float blendOut, Quaternionf[][] rotations,
                                         Vector3f[][] translations, PenguinAnimationClip reference)
        throws IOException {
        JsonObject asset = new JsonObject();
        asset.addProperty("schema", 1);
        asset.addProperty("name", name);
        asset.addProperty("source", "Mixamo DAE directional retarget; source SHA-256 "
            + DanceLibrary.sha256(source));
        asset.addProperty("source_sha256", DanceLibrary.sha256(source));
        asset.addProperty("blend_in", blendIn);
        asset.addProperty("blend_out", blendOut);
        asset.addProperty("duration", times[times.length - 1]);
        JsonArray joints = new JsonArray();
        for (int i = 0; i < PenguinRig.COUNT; i++) {
            JsonObject joint = new JsonObject();
            joint.addProperty("name", PenguinRig.NAMES[i]);
            joint.addProperty("parent", PenguinRig.PARENTS[i]);
            JsonArray bind = new JsonArray();
            Vector3f vector = reference.bind(i);
            bind.add(vector.x);
            bind.add(vector.y);
            bind.add(vector.z);
            joint.add("bind", bind);
            joints.add(joint);
        }
        asset.add("joints", joints);
        JsonArray frames = new JsonArray();
        for (int frame = 0; frame < times.length; frame++) {
            JsonObject item = new JsonObject();
            item.addProperty("time", times[frame]);
            JsonArray q = new JsonArray();
            JsonArray p = new JsonArray();
            JsonArray s = new JsonArray();
            for (int i = 0; i < PenguinRig.COUNT; i++) {
                Quaternionf rotation = rotations[frame][i];
                JsonArray values = new JsonArray();
                values.add(rotation.x);
                values.add(rotation.y);
                values.add(rotation.z);
                values.add(rotation.w);
                q.add(values);
                Vector3f translation = translations[frame][i];
                JsonArray offset = new JsonArray();
                offset.add(translation.x);
                offset.add(translation.y);
                offset.add(translation.z);
                p.add(offset);
                JsonArray one = new JsonArray();
                one.add(1);
                one.add(1);
                one.add(1);
                s.add(one.deepCopy());
            }
            item.add("rotations", q);
            item.add("translations", p);
            item.add("scales", s);
            frames.add(item);
        }
        asset.add("frames", frames);
        return GSON.toJson(asset).getBytes(StandardCharsets.UTF_8);
    }

    private static PenguinAnimationClip taunt() throws IOException {
        PenguinAnimationClip cached = tauntClip;
        if (cached != null) {
            return cached;
        }
        try (var stream = DanceImport.class.getResourceAsStream(TAUNT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Canonical taunt animation resource is missing");
            }
            try (Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                cached = PenguinAnimationClip.parse(reader);
            }
            tauntClip = cached;
            return cached;
        } catch (RuntimeException e) {
            throw new IOException("Canonical taunt animation is invalid", e);
        }
    }

    private static String safeName(String fileName) {
        String value = fileName == null ? "" : fileName;
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        value = value.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip();
        if (value.isEmpty()) {
            value = "dance";
        }
        return value.length() > DanceLibrary.MAX_TITLE_CHARS
            ? value.substring(0, DanceLibrary.MAX_TITLE_CHARS) : value;
    }

    private static String normalizeBoneName(String value) {
        String name = value == null ? "" : value.strip();
        if (name.startsWith("mixamorig:")) {
            name = name.substring("mixamorig:".length());
        } else if (name.startsWith("mixamorig_")) {
            name = name.substring("mixamorig_".length());
        }
        return name;
    }

    private static XNode parseXml(byte[] source) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        setProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        setProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        setProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, false);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(source));
            XNode root = null;
            Deque<XNode> stack = new ArrayDeque<>();
            int depth = 0;
            int nodes = 0;
            long textChars = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        depth++;
                        if (depth > MAX_XML_DEPTH) {
                            throw new IOException("DAE XML nesting exceeds " + MAX_XML_DEPTH);
                        }
                        if (++nodes > MAX_XML_NODES) {
                            throw new IOException("DAE XML node budget exceeded");
                        }
                        Map<String, String> attrs = new LinkedHashMap<>();
                        if (reader.getAttributeCount() > 64) {
                            throw new IOException("DAE XML element has too many attributes");
                        }
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrs.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                        }
                        XNode node = new XNode(reader.getLocalName(), attrs);
                        if (stack.isEmpty()) {
                            if (root != null) {
                                throw new IOException("DAE XML has multiple root elements");
                            }
                            root = node;
                        } else {
                            stack.peek().children.add(node);
                        }
                        stack.push(node);
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (stack.isEmpty()) {
                            throw new IOException("DAE XML has an unmatched end element");
                        }
                        stack.pop();
                        depth--;
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (!stack.isEmpty()) {
                            String text = reader.getText();
                            textChars += text.length();
                            if (textChars > MAX_XML_TEXT_CHARS) {
                                throw new IOException("DAE XML text budget exceeded");
                            }
                            stack.peek().text.append(text);
                        }
                    }
                    case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE ->
                        throw new IOException("DAE DTD and entity references are disabled");
                    default -> {
                    }
                }
            }
            reader.close();
            if (root == null) {
                throw new IOException("DAE XML is empty");
            }
            return root;
        } catch (XMLStreamException e) {
            throw new IOException("Malformed DAE XML", e);
        }
    }

    private static void setProperty(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException ignored) {
            // A JDK implementation may not expose every optional safety property. DTD events are
            // still rejected explicitly in parseXml, so the import remains bounded.
        }
    }

    private static XNode child(XNode parent, String name) {
        if (parent == null) {
            return null;
        }
        for (XNode child : parent.children) {
            if (name.equals(child.name)) {
                return child;
            }
        }
        return null;
    }

    private static List<XNode> childrenNamed(XNode parent, String name) {
        List<XNode> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        for (XNode child : parent.children) {
            if (name.equals(child.name)) {
                result.add(child);
            }
        }
        return result;
    }

    private static String textOf(XNode node) {
        return node == null ? "" : node.text.toString();
    }

    private static float[] parseFloats(String text, String what) throws IOException {
        String trim = text == null ? "" : text.trim();
        if (trim.isEmpty()) {
            return new float[0];
        }
        String[] tokens = trim.split("\\s+");
        float[] values = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                values[i] = Float.parseFloat(tokens[i]);
            } catch (NumberFormatException e) {
                throw new IOException("DAE " + what + " has an invalid number at index " + i, e);
            }
            if (!finite(values[i])) {
                throw new IOException("DAE " + what + " has a non-finite number at index " + i);
            }
        }
        return values;
    }

    private static double finiteDouble(String value, String what) throws IOException {
        if (value == null) {
            throw new IOException("DAE " + what + " is missing");
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new IOException("DAE " + what + " is not finite");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IOException("DAE " + what + " is not a number", e);
        }
    }

    private static int finiteInt(String value, String what) throws IOException {
        if (value == null) {
            throw new IOException("DAE " + what + " is missing");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("DAE " + what + " is not an integer", e);
        }
    }

    private static boolean finite(float value) {
        return Float.isFinite(value);
    }

    private static final class XNode {
        private final String name;
        private final Map<String, String> attrs;
        private final List<XNode> children = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        private XNode(String name, Map<String, String> attrs) {
            this.name = name;
            this.attrs = attrs;
        }

        private String attr(String name) {
            return attrs.get(name);
        }
    }

    private static final class Bone {
        private final String name;
        private final Matrix4f restLocal;
        private final Bone parent;
        private final List<Bone> children = new ArrayList<>();

        private Bone(String name, Matrix4f restLocal, Bone parent) {
            this.name = name;
            this.restLocal = restLocal;
            this.parent = parent;
        }
    }
}
