package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.CatModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * One static, frame-independent prone pose for the seven native animals.
 *
 * <p>Local axes before the whole-body transform are the shared penguin model axes: {@code +Y} runs
 * from the neck/front towards the feet/tail, {@code -Z} is forward and {@code +X} is the penguin's
 * anatomical left. The body mesh is built upright, so the whole five-root assembly is laid down by
 * a single +90 degree X rotation in {@link #applyBodyTransform}. That maps the old front/belly
 * ({@code -Z}) onto renderer {@code +Y} (down) and the old {@code +Y} tail direction onto
 * {@code +Z} (behind the entity). Rotating the assembly once, rather than just the torso, is what
 * keeps the wings and feet trailing horizontally instead of standing upright through the ground.
 *
 * <p>The native head is never part of this rotation. {@link #applyHead} only translates the
 * original native head pivot onto the body's front neck origin in the renderer root frame, so
 * native size, yaw, pitch and child offsets (wolf {@code real_head}, cow horns, pig snout) are
 * preserved. The renderer root frame has {@code +Y} down and model {@code Y=24} on the ground; see
 * {@code LivingEntityRenderer.render}'s flip/translate pair. The constants below are therefore all
 * expressed in native model pixels measured from that same root.
 */
final class PenguinPronePose {
    /** Renderer-root model pixel that lands on the ground for every vanilla mob renderer. */
    private static final float GROUND_PIXELS = 24.0F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    /** The body mesh forward/belly direction (-Z) becomes downward (+Y) after the X rotation. */
    private static final float LAY_DOWN_DEGREES = 90.0F;
    /** Splay the flat wings outside the widening torso so they trail along the sides. */
    private static final float WING_SPLAY_Z = 0.55F;
    private static final float WING_TRAIL_X = -0.12F;

    private record FamilyProfile(float bodyScale, float neckZPixels) {
    }

    /** Renderer-root front-neck origin of the prepared body, in blocks. */
    private record BodyOrigin(float y, float z) {
    }

    // Scales are tuned against the native head width and body length of each family. Cave spider
    // reuses SPIDER: its renderer's own .7 overall scale is left untouched.
    private static final FamilyProfile COW = new FamilyProfile(0.80F, -8.0F);
    private static final FamilyProfile SHEEP = new FamilyProfile(0.68F, -8.0F);
    private static final FamilyProfile PIG = new FamilyProfile(0.66F, -6.0F);
    private static final FamilyProfile CAT = new FamilyProfile(0.45F, -9.0F);
    private static final FamilyProfile WOLF = new FamilyProfile(0.68F, -7.0F);
    private static final FamilyProfile SPIDER = new FamilyProfile(0.70F, -3.0F);

    /** Cube-geometry floor bound of the static pose; immutable, so compute it only once. */
    private static volatile Float cubeFloorNegZ;
    /**
     * Floor bounds of the immutable mesh tiers. Weak keys let a resource reload drop unloaded
     * tiers instead of retaining every old mesh through this cache.
     */
    private static final Map<PenguinMeshModel, Float> MESH_FLOOR_NEG_Z = new WeakHashMap<>();

    private PenguinPronePose() {
    }

    static FamilyProfile forModel(EntityModel<?> model) {
        if (model instanceof CowModel<?>) {
            return COW;
        }
        if (model instanceof SheepModel<?> || model instanceof SheepFurModel<?>) {
            return SHEEP;
        }
        if (model instanceof PigModel<?>) {
            return PIG;
        }
        // CatModel is a subclass of OcelotModel; only the listed cat is admitted here.
        if (model instanceof CatModel<?>) {
            return CAT;
        }
        if (model instanceof WolfModel<?>) {
            return WOLF;
        }
        // SpiderModel covers both spider and cave spider; the cave renderer keeps its .7 scale.
        if (model instanceof SpiderModel<?>) {
            return SPIDER;
        }
        return null;
    }

    /** True for models owned by the seven prone families and their head-only layer copies. */
    static boolean isProneModel(EntityModel<?> model) {
        return forModel(model) != null;
    }

    /**
     * Binds the shared five roots to the prone pose. Called from the same reset path as every
     * other adapter so one entity cannot leak limbs into the next. No limb rotation depends on
     * limbSwing or walk speed; movement can only translate the entity.
     */
    static void applyStatic(PenguinBodyModel penguin) {
        penguin.resetPose();
        // -Z rotation swings a down-pointing right wing towards +X; the left wing mirrors it.
        penguin.rightWing().zRot = -WING_SPLAY_Z;
        penguin.leftWing().zRot = WING_SPLAY_Z;
        penguin.rightWing().xRot = WING_TRAIL_X;
        penguin.leftWing().xRot = WING_TRAIL_X;
        // Feet keep their bind pose: after lay-down they point backwards along the ground.
    }

    /**
     * Applies the single whole-body transform in {@link PenguinBodyLayer} before all five roots
     * are drawn. Transform order is translate (neck origin) -> uniform scale -> lay-down rotation.
     * The Y translation is derived from the actual lower bound of the static pose so the belly,
     * trailing feet and wings sit on the renderer ground plane for both the cube body and each
     * mesh tier. Z translation puts the body's front neck at the family neck anchor.
     */
    static void applyBodyTransform(PoseStack poseStack, PenguinBodyModel penguin,
                                   EntityModel<?> model, PenguinMeshModel mesh) {
        FamilyProfile profile = forModel(model);
        if (profile == null) {
            return;
        }
        BodyOrigin origin = bodyOrigin(profile, floorNegZ(penguin, mesh));
        poseStack.translate(0.0F, origin.y(), origin.z());
        poseStack.scale(profile.bodyScale(), profile.bodyScale(), profile.bodyScale());
        poseStack.mulPose(Axis.XP.rotationDegrees(LAY_DOWN_DEGREES));
    }

    /**
     * Translates the original native head pivot onto the same prepared body neck origin used by
     * {@link #applyBodyTransform}. The head gets a rigid translation only: no scale and no
     * rotation, and its native {@code head.x} plus all child offsets are kept. The pivot is read
     * every frame, so native grazing translations are re-anchored to the neck without moving it;
     * yaw and pitch are never touched. The caller must prepare the shared body first so the head
     * uses exactly the same mesh tier floor bound as the body that is being drawn.
     */
    static void applyHead(PoseStack poseStack, EntityModel<?> model, PenguinBodyModel penguin,
                          PenguinMeshModel mesh) {
        FamilyProfile profile = forModel(model);
        ModelPart head = PenguinHeadRenderer.headPart(model);
        if (profile == null || head == null) {
            return;
        }
        BodyOrigin origin = bodyOrigin(profile, floorNegZ(penguin, mesh));
        poseStack.translate(0.0F,
                origin.y() - head.y / PIXELS_PER_BLOCK,
                origin.z() - head.z / PIXELS_PER_BLOCK);
    }

    /**
     * One shared equation for the body and head anchors. The lay-down rotation maps the prepared
     * body's largest {@code -Z} onto renderer {@code +Y}; subtracting the scaled bound puts that
     * floor exactly on {@link #GROUND_PIXELS}. The Z term is the family neck anchor.
     */
    private static BodyOrigin bodyOrigin(FamilyProfile profile, float floorNegZ) {
        return new BodyOrigin(
                GROUND_PIXELS / PIXELS_PER_BLOCK - profile.bodyScale() * floorNegZ,
                profile.neckZPixels() / PIXELS_PER_BLOCK);
    }

    private static float floorNegZ(PenguinBodyModel penguin, PenguinMeshModel mesh) {
        if (mesh != null) {
            synchronized (MESH_FLOOR_NEG_Z) {
                Float cached = MESH_FLOOR_NEG_Z.get(mesh);
                if (cached == null) {
                    cached = mesh.maxNegZ(penguin);
                    MESH_FLOOR_NEG_Z.put(mesh, cached);
                }
                return cached;
            }
        }
        Float cached = cubeFloorNegZ;
        if (cached == null) {
            cached = cubeMaxNegZ(penguin);
            cubeFloorNegZ = cached;
        }
        return cached;
    }

    /**
     * The lay-down rotation maps local Z to renderer Y, so the lowest point after rotation is the
     * largest {@code -Z} value in the static pose. Values are block-space after the ModelPart
     * transforms (the visitor already applies the /16 pixel conversion to cube corners).
     */
    private static float cubeMaxNegZ(PenguinBodyModel penguin) {
        float[] max = {Float.NEGATIVE_INFINITY};
        Vector3f point = new Vector3f();
        ModelPart.Visitor visitor = (pose, path, index, cube) -> {
            for (int corner = 0; corner < 8; corner++) {
                point.set((corner & 1) == 0 ? cube.minX : cube.maxX,
                        (corner & 2) == 0 ? cube.minY : cube.maxY,
                        (corner & 4) == 0 ? cube.minZ : cube.maxZ).div(PIXELS_PER_BLOCK);
                pose.pose().transformPosition(point);
                max[0] = Math.max(max[0], -point.z);
            }
        };
        for (ModelPart part : new ModelPart[]{penguin.body(), penguin.rightWing(),
                penguin.leftWing(), penguin.rightFoot(), penguin.leftFoot()}) {
            part.visit(new PoseStack(), visitor);
        }
        return max[0];
    }
}
