package com.luochun.kusopenguin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable, reload-cached representation of the upstream Blockbench low-poly prototype.
 *
 * <p>The JSON is parsed only by {@link ReloadListener} during a client resource load/reload.
 * Rendering walks the cached triangle lists and never performs IO or JSON parsing. Geometry is
 * expressed in Java model pixels relative to each animation root; this class only renders it, and
 * the existing {@link PenguinBodyModel} parts still supply the animated transforms.
 */
public final class PenguinMeshModel {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            KusoPenguin.MODID, "textures/entity/penguin_lowpoly.png");
    private static final Map<PenguinMeshPreview.Quality, ResourceLocation> MODELS = Map.of(
            PenguinMeshPreview.Quality.HIGH, meshResource("penguin_lowpoly.json"),
            PenguinMeshPreview.Quality.MEDIUM, meshResource("penguin_lowpoly_medium.json"),
            PenguinMeshPreview.Quality.LOW, meshResource("penguin_lowpoly_low.json"));
    private static final Logger LOGGER = LoggerFactory.getLogger("kuso_penguin/mesh_preview");
    private static final float PIXEL = 16.0F;
    private static final Set<String> EXPECTED_PARTS = Set.of(
            "body", "right_wing", "left_wing", "right_foot", "left_foot");
    /** Bind pivots of the existing five Java animation roots, in Java model pixels. */
    private static final Map<String, float[]> EXPECTED_PIVOTS = Map.of(
            "body", new float[]{0.0F, 0.0F, 0.0F},
            "right_wing", new float[]{5.45F, 2.6F, 0.2F},
            "left_wing", new float[]{-5.45F, 2.6F, 0.2F},
            "right_foot", new float[]{3.3F, 15.0F, 0.0F},
            "left_foot", new float[]{-3.3F, 15.0F, 0.0F});
    private static final double PIVOT_EPSILON = 1.0E-4D;
    private static final double UV_EPSILON = 1.0E-6D;
    private static final double NORMAL_TOLERANCE = 1.0E-3D;
    private static final double NORMAL_LENGTH_EPSILON = 1.0E-9D;

    private static final ReloadListener RELOAD_LISTENER = new ReloadListener();
    private record Loaded(Map<PenguinMeshPreview.Quality,PenguinMeshModel> meshes,
                          Map<PenguinMeshPreview.Quality,PenguinSkinnedMesh> skins,
                          Map<PenguinAction,PenguinAnimationClip> clips,
                          PenguinSkinnedMesh ignisSkin) {}
    private static volatile Loaded cache;

    private final Part body;
    private final Part rightWing;
    private final Part leftWing;
    private final Part rightFoot;
    private final Part leftFoot;
    /**
     * Schema 2 may mix 8-float automatic vertices (body/wings) with 11-float explicit foot
     * vertices. This flag is the single load-time decision for the body-only native fallback and
     * for the weighted floor bounds; it is never re-read per frame.
     */
    private final boolean hasExplicitBindings;

    private PenguinMeshModel(Part body, Part rightWing, Part leftWing, Part rightFoot,
                             Part leftFoot, boolean hasExplicitBindings) {
        this.body = body;
        this.rightWing = rightWing;
        this.leftWing = leftWing;
        this.rightFoot = rightFoot;
        this.leftFoot = leftFoot;
        this.hasExplicitBindings = hasExplicitBindings;
    }

    boolean hasExplicitBindings() {
        return hasExplicitBindings;
    }

    /**
     * The selected tier from the most recent complete resource load, or {@code null} before it.
     * While the preview is off the high tier is still returned so the already-loaded cache remains
     * observable; the body layer decides whether to draw it.
     */
    public static PenguinMeshModel current() {
        Loaded loaded = cache;
        if (loaded == null) {
            return null;
        }
        PenguinMeshPreview.Quality quality = PenguinMeshPreview.quality();
        if (quality == PenguinMeshPreview.Quality.BLOCK) {
            quality = PenguinMeshPreview.Quality.HIGH;
        }
        return loaded.meshes().get(quality);
    }

    List<Part> animationParts(){return List.of(body,rightWing,leftWing,rightFoot,leftFoot);}

    public static PenguinAnimationClip animationClip(){return animationClip(PenguinAction.TAUNT);}
    public static PenguinAnimationClip animationClip(PenguinAction action){
        Loaded loaded=cache;return loaded==null?null:loaded.clips().get(action);
    }
    public static PenguinSkinnedMesh currentSkin(){
        Loaded loaded=cache;
        return loaded==null||!PenguinMeshPreview.isEnabled()?null:loaded.skins().get(PenguinMeshPreview.quality());
    }

    /** Single pilot tier: original fat body/connected feet with custom cuffs, baked on reload. */
    public static PenguinSkinnedMesh ignisSkin(){
        Loaded loaded=cache;
        return loaded==null||!PenguinMeshPreview.isEnabled()?null:loaded.ignisSkin();
    }

    /**
     * Identity lookup of the already-baked skin for this exact tier. Used by weighted bounds so a
     * non-current tier never borrows another tier's topology through the global preview quality.
     */
    static PenguinSkinnedMesh skinFor(PenguinMeshModel mesh){
        Loaded loaded=cache;
        if(loaded==null)return null;
        for(Map.Entry<PenguinMeshPreview.Quality,PenguinMeshModel> entry:loaded.meshes().entrySet()){
            if(entry.getValue()==mesh)return loaded.skins().get(entry.getKey());
        }
        return null;
    }

    public static SimplePreparableReloadListener<Loaded>
            reloadListener() {
        return RELOAD_LISTENER;
    }

    private static ResourceLocation meshResource(String name) {
        return ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "models/entity/" + name);
    }

    /**
     * Draws the five mesh roots using the already-adapted animation transforms on the shared
     * penguin model. Each triangle is submitted as a degenerate quad so the standard entity QUADS
     * render type cannot stitch vertices across neighboring triangles.
     */
    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                       int packedOverlay, int color, PenguinBodyModel parts) {
        renderPart(poseStack, buffer, packedLight, packedOverlay, color, parts.body(), this.body);
        renderPart(poseStack, buffer, packedLight, packedOverlay, color, parts.rightWing(),
                this.rightWing);
        renderPart(poseStack, buffer, packedLight, packedOverlay, color, parts.leftWing(),
                this.leftWing);
        renderPart(poseStack, buffer, packedLight, packedOverlay, color, parts.rightFoot(),
                this.rightFoot);
        renderPart(poseStack, buffer, packedLight, packedOverlay, color, parts.leftFoot(),
                this.leftFoot);
    }

    /**
     * Model-block upper bound of one source part, using the same prepared five roots for the
     * whole assembly. Schema 2 legs must be measured from their actual deformed skin points
     * (pelvis-pinned seam vertices stay on the torso); schema 1 keeps the old rigid root bound.
     */
    float maxY(PenguinBodyModel parts, String name) {
        Part meshPart = meshPart(name);
        if (hasExplicitBindings) {
            PenguinSkinnedMesh skin = skinFor(this);
            PenguinAnimationClip bind = animationClip();
            if (skin == null || bind == null) {
                throw new IllegalStateException("Weighted penguin mesh is missing its baked skin");
            }
            return skin.maxY(PenguinAnimationPose.nativeBodyOnly(parts, bind), name) / PIXEL;
        }
        ModelPart modelPart = switch(name) {
            case "body" -> parts.body();
            case "right_foot" -> parts.rightFoot();
            case "left_foot" -> parts.leftFoot();
            default -> throw new IllegalArgumentException(name);
        };
        PoseStack stack = new PoseStack();modelPart.translateAndRotate(stack);
        org.joml.Vector3f point = new org.joml.Vector3f();
        float max = Float.NEGATIVE_INFINITY;
        for(Triangle triangle:meshPart.triangles()) {
            max=Math.max(max,transformedY(triangle.a(),stack.last().pose(),point));
            max=Math.max(max,transformedY(triangle.b(),stack.last().pose(),point));
            max=Math.max(max,transformedY(triangle.c(),stack.last().pose(),point));
        }
        return max;
    }

    private Part meshPart(String name) {
        return switch(name) {
            case "body" -> this.body;
            case "right_foot" -> this.rightFoot;
            case "left_foot" -> this.leftFoot;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static float transformedY(Vertex vertex,org.joml.Matrix4fc matrix,org.joml.Vector3f point) {
        point.set(vertex.x()/PIXEL,vertex.y()/PIXEL,vertex.z()/PIXEL);matrix.transformPosition(point);return point.y;
    }

    /**
     * Largest {@code -Z} extent of all five static roots in model blocks. The prone lay-down
     * rotation maps local Z onto renderer Y, so this is the floor bound used by
     * {@link PenguinPronePose}. The result is immutable per loaded tier and is cached there.
     */
    float maxNegZ(PenguinBodyModel parts) {
        if (hasExplicitBindings) {
            PenguinSkinnedMesh skin = skinFor(this);
            PenguinAnimationClip bind = animationClip();
            if (skin == null || bind == null) {
                throw new IllegalStateException("Weighted penguin mesh is missing its baked skin");
            }
            return skin.maxNegZ(PenguinAnimationPose.nativeBodyOnly(parts, bind)) / PIXEL;
        }
        float max = Float.NEGATIVE_INFINITY;
        max = Math.max(max, maxNegZ(parts.body(), this.body));
        max = Math.max(max, maxNegZ(parts.rightWing(), this.rightWing));
        max = Math.max(max, maxNegZ(parts.leftWing(), this.leftWing));
        max = Math.max(max, maxNegZ(parts.rightFoot(), this.rightFoot));
        max = Math.max(max, maxNegZ(parts.leftFoot(), this.leftFoot));
        return max;
    }

    private static float maxNegZ(ModelPart modelPart, Part meshPart) {
        PoseStack stack = new PoseStack();
        modelPart.translateAndRotate(stack);
        org.joml.Matrix4f matrix = new org.joml.Matrix4f(stack.last().pose());
        org.joml.Vector3f point = new org.joml.Vector3f();
        float max = Float.NEGATIVE_INFINITY;
        for (Triangle triangle : meshPart.triangles()) {
            max = Math.max(max, negZ(triangle.a(), matrix, point));
            max = Math.max(max, negZ(triangle.b(), matrix, point));
            max = Math.max(max, negZ(triangle.c(), matrix, point));
        }
        return max;
    }

    private static float negZ(Vertex vertex, org.joml.Matrix4fc matrix,
                              org.joml.Vector3f point) {
        point.set(vertex.x() / PIXEL, vertex.y() / PIXEL, vertex.z() / PIXEL);
        matrix.transformPosition(point);
        return -point.z;
    }

    private static void renderPart(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, int color, ModelPart modelPart,
                                   Part meshPart) {
        poseStack.pushPose();
        try {
            modelPart.translateAndRotate(poseStack);
            PoseStack.Pose pose = poseStack.last();
            for (Triangle triangle : meshPart.triangles()) {
                vertex(buffer, pose, triangle.a(), packedLight, packedOverlay, color);
                vertex(buffer, pose, triangle.b(), packedLight, packedOverlay, color);
                vertex(buffer, pose, triangle.c(), packedLight, packedOverlay, color);
                vertex(buffer, pose, triangle.c(), packedLight, packedOverlay, color);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vertex vertex,
                               int packedLight, int packedOverlay, int color) {
        buffer.addVertex(pose, vertex.x() / PIXEL, vertex.y() / PIXEL, vertex.z() / PIXEL)
                .setColor(color)
                .setUv(vertex.u(), vertex.v())
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
    }

    private static PenguinMeshModel parse(Reader reader) throws IOException {
        List<Part> parsedParts = parseParts(reader, EXPECTED_PARTS);
        Part body = find(parsedParts, "body");
        Part rightWing = find(parsedParts, "right_wing");
        Part leftWing = find(parsedParts, "left_wing");
        Part rightFoot = find(parsedParts, "right_foot");
        Part leftFoot = find(parsedParts, "left_foot");
        boolean hasExplicitBindings = parsedParts.stream().flatMap(p -> p.triangles().stream())
                .anyMatch(t -> t.a().binding() != null);
        return new PenguinMeshModel(body,rightWing,leftWing,rightFoot,leftFoot,hasExplicitBindings);
    }

    /** Same strict schema/pivot/face checks, with an explicit root contract per asset. */
    private static List<Part> parseParts(Reader reader, Set<String> expectedParts) throws IOException {
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed penguin mesh JSON", exception);
        }
        if (!rootElement.isJsonObject()) {
            throw new IOException("Penguin mesh root must be a JSON object");
        }
        JsonObject root = rootElement.getAsJsonObject();
        JsonElement schema = root.get("schema");
        if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                || (schema.getAsDouble() != 1.0D && schema.getAsDouble() != 2.0D)) {
            throw new IOException("Penguin mesh schema must be exactly the number 1 or 2");
        }
        boolean explicitAllowed = schema.getAsDouble() == 2.0D;
        JsonElement partsElement = root.get("parts");
        if (partsElement == null || !partsElement.isJsonArray()) {
            throw new IOException("Penguin mesh parts must be an array");
        }
        JsonArray partsArray = partsElement.getAsJsonArray();
        if (partsArray.size() != expectedParts.size()) {
            throw new IOException("Penguin mesh must contain exactly "
                    + expectedParts.size() + " parts, found " + partsArray.size());
        }

        List<Part> parsedParts = new ArrayList<>(partsArray.size());
        Set<String> names = new HashSet<>();
        for (int i = 0; i < partsArray.size(); i++) {
            Part part = parsePart(partsArray.get(i), "parts[" + i + "]", explicitAllowed);
            if (!expectedParts.contains(part.name())) {
                throw new IOException("parts[" + i + "] has unknown name '" + part.name() + "'");
            }
            if (!names.add(part.name())) {
                throw new IOException("Duplicate penguin mesh part '" + part.name() + "'");
            }
            parsedParts.add(part);
        }
        if (!names.equals(expectedParts)) {
            throw new IOException("Penguin mesh is missing required parts: " + expectedParts
                    + " (found " + names + ")");
        }
        return List.copyOf(parsedParts);
    }

    private static Part find(List<Part> parts, String name) throws IOException {
        for (Part part : parts) {
            if (part.name().equals(name)) {
                return part;
            }
        }
        throw new IOException("Missing penguin mesh part '" + name + "'");
    }

    private static Part parsePart(JsonElement element, String path, boolean explicitAllowed)
            throws IOException {
        if (!element.isJsonObject()) {
            throw new IOException(path + " must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement nameElement = object.get("name");
        if (nameElement == null || !nameElement.isJsonPrimitive()
                || !nameElement.getAsJsonPrimitive().isString()) {
            throw new IOException(path + ".name must be a string");
        }
        String name = nameElement.getAsString();

        Vertex pivot = parseVertexNumbers(object.get("pivot"), path + ".pivot", 3);
        float[] expectedPivot = EXPECTED_PIVOTS.get(name);
        if (expectedPivot == null) {
            throw new IOException(path + ".name has unknown part '" + name + "'");
        }
        if (!pivotMatches(pivot, expectedPivot)) {
            throw new IOException(path + ".pivot for '" + name + "' is ["
                    + pivot.x() + ", " + pivot.y() + ", " + pivot.z()
                    + "], expected [" + expectedPivot[0] + ", " + expectedPivot[1] + ", "
                    + expectedPivot[2] + "]");
        }
        JsonElement trianglesElement = object.get("triangles");
        if (trianglesElement == null || !trianglesElement.isJsonArray()) {
            throw new IOException(path + ".triangles must be an array");
        }
        JsonArray trianglesArray = trianglesElement.getAsJsonArray();
        if (trianglesArray.isEmpty()) {
            throw new IOException(path + ".triangles must not be empty");
        }
        List<Triangle> triangles = new ArrayList<>(trianglesArray.size());
        for (int i = 0; i < trianglesArray.size(); i++) {
            triangles.add(parseTriangle(trianglesArray.get(i),
                    path + ".triangles[" + i + "]", explicitAllowed));
        }
        return new Part(name, pivot.x(), pivot.y(), pivot.z(),
                List.copyOf(triangles));
    }

    private static boolean pivotMatches(Vertex pivot, float[] expected) {
        return Math.abs((double) pivot.x() - expected[0]) <= PIVOT_EPSILON
                && Math.abs((double) pivot.y() - expected[1]) <= PIVOT_EPSILON
                && Math.abs((double) pivot.z() - expected[2]) <= PIVOT_EPSILON;
    }

    private static Triangle parseTriangle(JsonElement element, String path, boolean explicitAllowed)
            throws IOException {
        if (!element.isJsonArray()) {
            throw new IOException(path + " must be an array of three vertices");
        }
        JsonArray points = element.getAsJsonArray();
        if (points.size() != 3) {
            throw new IOException(path + " must have exactly 3 vertices, found " + points.size());
        }
        Vertex a = parseVertex(points.get(0), path + "[0]", explicitAllowed);
        Vertex b = parseVertex(points.get(1), path + "[1]", explicitAllowed);
        Vertex c = parseVertex(points.get(2), path + "[2]", explicitAllowed);
        boolean any = a.binding() != null || b.binding() != null || c.binding() != null;
        boolean all = a.binding() != null && b.binding() != null && c.binding() != null;
        if (any && !all) {
            throw new IOException(path + " must be entirely explicit or entirely automatic");
        }
        return new Triangle(a, b, c);
    }

    private static Vertex parseVertex(JsonElement element, String path, boolean explicitAllowed)
            throws IOException {
        if (!element.isJsonArray()) {
            throw new IOException(path + " must be an array of 8 or 11 numbers");
        }
        JsonArray values = element.getAsJsonArray();
        boolean explicit = values.size() == 11;
        if (explicit && !explicitAllowed) {
            throw new IOException(path + " explicit bindings require schema 2");
        }
        if (!explicit && values.size() != 8) {
            throw new IOException(path + " must have exactly 8 or 11 numbers, found "
                    + values.size());
        }
        double[] v = new double[8];
        for (int i = 0; i < 8; i++) {
            JsonElement value = values.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IOException(path + "[" + i + "] must be a number");
            }
            v[i] = value.getAsDouble();
            if (!Double.isFinite(v[i])) {
                throw new IOException(path + "[" + i + "] must be finite");
            }
        }
        float[] f = new float[8];
        for (int i = 0; i < 8; i++) {
            f[i] = (float) v[i];
            if (!Float.isFinite(f[i])) {
                throw new IOException(path + "[" + i + "] overflows after conversion to float");
            }
        }
        if (f[3] < -UV_EPSILON || f[3] > 1.0D + UV_EPSILON
                || f[4] < -UV_EPSILON || f[4] > 1.0D + UV_EPSILON) {
            throw new IOException(path + " UV must be normalized to [0,1]");
        }
        double normalLength = Math.sqrt(
                (double) f[5] * f[5] + (double) f[6] * f[6] + (double) f[7] * f[7]);
        if (!Double.isFinite(normalLength) || normalLength <= NORMAL_LENGTH_EPSILON) {
            throw new IOException(path + " normal must be non-zero");
        }
        if (Math.abs(normalLength - 1.0D) > NORMAL_TOLERANCE) {
            throw new IOException(path + " normal must be unit length (found " + normalLength + ")");
        }
        Binding binding = null;
        if (explicit) {
            int jointA = parseJoint(values.get(8), path + "[8]");
            int jointB = parseJoint(values.get(9), path + "[9]");
            JsonElement weightElement = values.get(10);
            if (!weightElement.isJsonPrimitive() || !weightElement.getAsJsonPrimitive().isNumber()) {
                throw new IOException(path + "[10] must be a number");
            }
            double weight = weightElement.getAsDouble();
            if (!Double.isFinite(weight) || weight < 0.0D || weight > 1.0D
                    || !Float.isFinite((float) weight)) {
                throw new IOException(path + "[10] weight must be finite in [0,1]");
            }
            binding = new Binding(jointA, jointB, (float) weight);
        }
        return new Vertex(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], binding);
    }

    private static int parseJoint(JsonElement element, String path) throws IOException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IOException(path + " joint must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < 0.0D || value > PenguinRig.COUNT - 1) {
            throw new IOException(path + " joint must be an integer in [0,"
                    + (PenguinRig.COUNT - 1) + "]");
        }
        return (int) value;
    }

    private static Vertex parseVertexNumbers(JsonElement element, String path, int expected)
            throws IOException {
        if (element == null || !element.isJsonArray()) {
            throw new IOException(path + " must be an array of " + expected + " numbers");
        }
        JsonArray values = element.getAsJsonArray();
        if (values.size() != expected) {
            throw new IOException(path + " must have exactly " + expected + " numbers, found "
                    + values.size());
        }
        float[] result = new float[expected];
        for (int i = 0; i < expected; i++) {
            JsonElement value = values.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IOException(path + "[" + i + "] must be a number");
            }
            float number = value.getAsFloat();
            if (!Float.isFinite(number)) {
                throw new IOException(path + "[" + i + "] must be finite");
            }
            result[i] = number;
        }
        return new Vertex(result[0], result[1], result[2], 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * Authoritative two-influence binding for one schema 2 vertex: joints are indices into the
     * shared 16-joint penguin rig and {@code weightB} is the weight of {@code jointB}.
     * Position/UV/normal remain the legacy 8 floats; no bind-space point or units change.
     */
    record Binding(int jointA, int jointB, float weightB) {
    }

    record Vertex(float x, float y, float z, float u, float v,
                          float nx, float ny, float nz, Binding binding) {
        Vertex(float x, float y, float z, float u, float v,
               float nx, float ny, float nz) {
            this(x, y, z, u, v, nx, ny, nz, null);
        }
    }

    record Triangle(Vertex a, Vertex b, Vertex c) {
    }

    record Part(String name, float pivotX, float pivotY, float pivotZ,
                        List<Triangle> triangles) {
        Part {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(triangles, "triangles");
            triangles = Collections.unmodifiableList(triangles);
        }
    }

    /**
     * Prepares all three tiers off-thread, then swaps one immutable cache on the game thread.
     * Any malformed tier fails the whole reload with a logged, explicit cause rather than
     * installing a partial map or silently falling back to another tier.
     */
    private static final class ReloadListener
            extends SimplePreparableReloadListener<Loaded> {
        @Override
        protected Loaded prepare(
                ResourceManager resourceManager, ProfilerFiller profiler) {
            try {
                Map<PenguinMeshPreview.Quality, PenguinMeshModel> parsed =
                        new EnumMap<>(PenguinMeshPreview.Quality.class);
                for (Map.Entry<PenguinMeshPreview.Quality, ResourceLocation> entry
                        : MODELS.entrySet()) {
                    ResourceLocation location = entry.getValue();
                    Resource resource = resourceManager.getResource(location)
                            .orElseThrow(() -> new IOException("Missing resource " + location));
                    try (Reader reader = new InputStreamReader(resource.open(),
                            StandardCharsets.UTF_8)) {
                        parsed.put(entry.getKey(), parse(reader));
                    }
                }
                ResourceLocation clipLocation=ResourceLocation.fromNamespaceAndPath("kuso_penguin","animations/taunt.json");
                Resource clipResource=resourceManager.getResource(clipLocation)
                        .orElseThrow(()->new IOException("Missing animation "+clipLocation));
                PenguinAnimationClip clip;
                try(Reader reader=new InputStreamReader(clipResource.open(),StandardCharsets.UTF_8)){
                    clip=PenguinAnimationClip.parse(reader);
                }
                Map<PenguinAction,PenguinAnimationClip> clips=new EnumMap<>(PenguinAction.class);
                clips.put(PenguinAction.TAUNT,clip);
                for(PenguinAction action:PenguinAction.values()){
                    if(action==PenguinAction.TAUNT)continue;
                    ResourceLocation location=action.animationId();
                    Resource resource=resourceManager.getResource(location)
                            .orElseThrow(()->new IOException("Missing animation "+location));
                    PenguinAnimationClip parsedClip;
                    try(Reader reader=new InputStreamReader(resource.open(),StandardCharsets.UTF_8)){
                        parsedClip=PenguinAnimationClip.parse(reader);
                    }
                    if(!parsedClip.sameRigAs(clip))throw new IOException("Animation rig mismatch for "+location);
                    clips.put(action,parsedClip);
                }
                Map<PenguinMeshPreview.Quality,PenguinSkinnedMesh> skins=new EnumMap<>(PenguinMeshPreview.Quality.class);
                for(var entry:parsed.entrySet())skins.put(entry.getKey(),PenguinSkinnedMesh.bake(entry.getValue(),clip));
                ResourceLocation ignisLocation=meshResource("cataclysm/ignis_legless.json");
                Resource ignisResource=resourceManager.getResource(ignisLocation)
                        .orElseThrow(()->new IOException("Missing resource "+ignisLocation));
                PenguinSkinnedMesh ignisSkin;
                try(Reader reader=new InputStreamReader(ignisResource.open(),StandardCharsets.UTF_8)){
                    ignisSkin=PenguinSkinnedMesh.bake(parseParts(reader,
                            EXPECTED_PARTS),clip);
                }
                return new Loaded(Map.copyOf(parsed),Map.copyOf(skins),Map.copyOf(clips),ignisSkin);
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Failed to load experimental penguin mesh tiers {}", MODELS.values(),
                        exception);
                throw new IllegalStateException(
                        "Invalid experimental penguin mesh resources " + MODELS.values(), exception);
            }
        }

        @Override
        protected void apply(Loaded models,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            cache = models;
        }
    }
}
