package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinAnimationPose;
import com.luochun.kusopenguin.client.PenguinBodyLayer;
import com.luochun.kusopenguin.client.PenguinBodyModel;
import com.luochun.kusopenguin.client.PenguinHeldItemRenderer;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.luochun.kusopenguin.client.PenguinRig;
import com.luochun.kusopenguin.client.PenguinProportions;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinMeshModel;
import com.luochun.kusopenguin.client.PenguinPoseAdapter;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional Iron's Spellbooks ordinary-humanoid Gecko4 bridge.
 *
 * <p>Rendering only: the seven whitelisted registry ids are not added to any action/AI/mana/
 * cooldown/collision set. The native GeoEntityRenderer still owns model hierarchy, animations,
 * renderer scale/yaw/death, labels and FX. This bridge snapshots the already-animated bones once
 * and draws the penguin body/head/held-item frames; it never mutates a native bone, runs
 * {@code handleAnimations}, restarts a clip or substitutes a Kuso dance/kick/taunt.
 *
 * <p>All Geo4 types live in the lazy {@link Impl}. The outer class only uses vanilla/NeoForge types,
 * so a dedicated server or a client without GeckoLib never links the optional API.
 */
public final class IronHumanoidCompatClient {
    private static final String IRONS_MOD_ID = "irons_spellbooks";
    private static final String GECKO_MOD_ID = "geckolib";
    private static final boolean AVAILABLE = ModList.get().isLoaded(IRONS_MOD_ID)
            && ModList.get().isLoaded(GECKO_MOD_ID);
    private static final Set<ResourceLocation> CASTERS = Set.of(
            id("pyromancer"), id("cryomancer"), id("necromancer"), id("archevoker"),
            id("priest"), id("apothecarist"), id("cultist"));
    // Nested Geo renders are possible (e.g. a render layer drawing another entity). These scopes
    // are stacks, not single overwrites: every push restores the exact outer frame on pop, so a
    // child render can never leave the parent replacement flag or active identity behind.
    private static final ThreadLocal<ArrayDeque<Active>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<HumanoidScope>> HUMANOID_RENDER =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final class HumanoidScope {
        private final Entity entity;
        private boolean replaced;

        private HumanoidScope(Entity entity) {
            this.entity = entity;
        }
    }

    private IronHumanoidCompatClient() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(IRONS_MOD_ID, path);
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Registry-based visual eligibility only; deliberately separate from the action-type sets. */
    public static boolean isWhitelisted(EntityType<?> type) {
        return AVAILABLE && CASTERS.contains(type.builtInRegistryHolder().key().location());
    }

    /** Python/Java-side readable path check used by comments/tests without an entity instance. */
    public static boolean isWhitelistedId(ResourceLocation id) {
        return AVAILABLE && CASTERS.contains(id);
    }

    private record Active(Entity entity, NativeCastingView view, Matrix4f rootPose) {
    }

    public static boolean isActiveFor(Entity entity) {
        Active active = ACTIVE.get().peek();
        return active != null && active.entity == entity;
    }

    /**
     * Called immediately before the native GeoRenderer traversal, after handleAnimations and the
     * native yaw/death/global scale transforms. The current PoseStack is therefore the one true
     * model-root frame for this native pass; the captured matrix is intentionally per pass, not a
     * claim that every re-render pass shares the first snapshot. Returns false and leaves
     * everything native when the entity is not one of the seven whitelisted casters or the model
     * does not have the complete required hierarchy.
     */
    public static boolean begin(Entity entity, Object bakedModel, PoseStack poseStack,
                                MultiBufferSource buffers, int packedLight, float partialTick,
                                boolean isReRender) {
        if (!AVAILABLE || !(entity instanceof LivingEntity living)
                || !ACTIVE.get().isEmpty()
                || !isWhitelisted(entity.getType())
                || !PenguinMeshModel.ready()
                || !PenguinRenderSupport.shouldReplace(living)) {
            return false;
        }
        // createView is the explicit preflight: an unsupported or non-canonical hierarchy returns
        // null. Unexpected exceptions are not swallowed here; nothing has been pushed yet.
        NativeCastingView view = Impl.createView(bakedModel);
        if (view == null || !view.kusoPenguin$ready()) {
            return false;
        }
        Matrix4f rootPose = new Matrix4f(poseStack.last().pose());
        PenguinRenderContext.push(living, (EntityModel<?>) view, partialTick);
        ACTIVE.get().push(new Active(entity, view, rootPose));
        try {
            if (!isReRender && buffers != null) {
                Impl.drawBody(poseStack, buffers, packedLight, living, view);
            }
        } catch (RuntimeException | Error exception) {
            // The token scope and the render context were created before the body draw. Roll both
            // back before rethrowing so a failed draw cannot leak state into later frames.
            rollback();
            throw exception;
        }
        return true;
    }

    private static void rollback() {
        ArrayDeque<Active> stack = ACTIVE.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ACTIVE.remove();
        }
        PenguinRenderContext.pop();
    }

    public static void end() {
        ArrayDeque<Active> stack = ACTIVE.get();
        Active active = stack.peek();
        if (active == null) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            ACTIVE.remove();
        }
        PenguinRenderContext.pop();
        ArrayDeque<HumanoidScope> scopes = HUMANOID_RENDER.get();
        HumanoidScope scope = scopes.peek();
        if (scope != null && scope.entity == active.entity) {
            // Only the matching outer HumanoidRenderer scope is marked; a nested render keeps its
            // own decision and is restored unchanged when its push/pop completes.
            scope.replaced = true;
        }
    }

    /** Marks the outer HumanoidRenderer.render scope so its hard-coded cape can be suppressed. */
    public static void beginHumanoidRender(Entity entity) {
        HUMANOID_RENDER.get().push(new HumanoidScope(entity));
    }

    public static void endHumanoidRender() {
        ArrayDeque<HumanoidScope> scopes = HUMANOID_RENDER.get();
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
        if (scopes.isEmpty()) {
            HUMANOID_RENDER.remove();
        }
    }

    public static boolean shouldSuppressCape(Entity entity) {
        HumanoidScope scope = HUMANOID_RENDER.get().peek();
        return scope != null && scope.entity == entity && scope.replaced;
    }

    public static boolean shouldSkipCubes(Entity entity, Object bone) {
        Active active = ACTIVE.get().peek();
        return active != null && active.entity == entity
                && Impl.ownsBone(active.view, bone)
                && Impl.isHeadSubtree(bone) == false;
    }

    public static boolean adjustHead(Entity entity, PoseStack poseStack, Object bone) {
        Active active = ACTIVE.get().peek();
        return active != null && active.entity == entity
                && Impl.ownsBone(active.view, bone)
                && Impl.adjustHead(poseStack, active, bone);
    }

    public static boolean adjustHand(Entity entity, PoseStack poseStack, Object bone) {
        Active active = ACTIVE.get().peek();
        return active != null && active.entity == entity
                && Impl.ownsBone(active.view, bone)
                && Impl.adjustHand(poseStack, active, bone);
    }

    public static boolean shouldSuppressArmorBone(Entity entity, Object bone) {
        Active active = ACTIVE.get().peek();
        return active != null && active.entity == entity
                && Impl.ownsBone(active.view, bone)
                && Impl.isSuppressedArmorBone(bone);
    }

    /**
     * Lazy Geo4 implementation. Only loaded after both optional mods are present and a whitelisted
     * caster actually renders; no common/server code links these classes.
     */
    private static final class Impl {
        private static final String HEAD = "head";
        private static final String BODY = "body";
        private static final String TORSO = "torso";
        private static final String RIGHT_ARM = "right_arm";
        private static final String LEFT_ARM = "left_arm";
        private static final String RIGHT_LEG = "right_leg";
        private static final String LEFT_LEG = "left_leg";
        private static final String RIGHT_HAND = "bipedHandRight";
        private static final String LEFT_HAND = "bipedHandLeft";
        private static final Set<String> REQUIRED = Set.of(BODY, TORSO, HEAD, RIGHT_ARM, LEFT_ARM,
                RIGHT_LEG, LEFT_LEG, RIGHT_HAND, LEFT_HAND);

        private Impl() {
        }

        private static NativeCastingView createView(Object bakedModel) {
            if (!(bakedModel instanceof software.bernie.geckolib.cache.object.BakedGeoModel baked)) {
                return null;
            }
            for (String name : REQUIRED) {
                if (baked.getBone(name).isEmpty()) {
                    return null;
                }
            }
            // Names alone are not enough: a different Gecko hierarchy could reuse them. Require the
            // canonical body-root tree and the stable body/head/arm/leg rest pivots before any
            // native cube/layer is filtered. Anything else keeps the complete native renderer.
            if (!canonicalHierarchy(baked)) {
                return null;
            }
            return new Proxy(baked);
        }

        private static boolean canonicalHierarchy(
                software.bernie.geckolib.cache.object.BakedGeoModel baked) {
            software.bernie.geckolib.cache.object.GeoBone body = boneOrNull(baked, BODY);
            software.bernie.geckolib.cache.object.GeoBone torso = boneOrNull(baked, TORSO);
            software.bernie.geckolib.cache.object.GeoBone head = boneOrNull(baked, HEAD);
            software.bernie.geckolib.cache.object.GeoBone rightArm = boneOrNull(baked, RIGHT_ARM);
            software.bernie.geckolib.cache.object.GeoBone leftArm = boneOrNull(baked, LEFT_ARM);
            software.bernie.geckolib.cache.object.GeoBone rightLeg = boneOrNull(baked, RIGHT_LEG);
            software.bernie.geckolib.cache.object.GeoBone leftLeg = boneOrNull(baked, LEFT_LEG);
            software.bernie.geckolib.cache.object.GeoBone rightHand = boneOrNull(baked, RIGHT_HAND);
            software.bernie.geckolib.cache.object.GeoBone leftHand = boneOrNull(baked, LEFT_HAND);
            if (body == null || torso == null || head == null || rightArm == null
                    || leftArm == null || rightLeg == null || leftLeg == null
                    || rightHand == null || leftHand == null) {
                return false;
            }
            if (body.getParent() != null
                    || torso.getParent() != body
                    || head.getParent() != body
                    || rightArm.getParent() != body
                    || leftArm.getParent() != body
                    || rightLeg.getParent() != body
                    || leftLeg.getParent() != body
                    || rightHand.getParent() != rightArm
                    || leftHand.getParent() != leftArm) {
                return false;
            }
            // Rest pivots are GeoBone values (JSON X is negated on load). Pin only the canonical
            // 3.16.3 body/head/arm/leg pivots; matching them also fixes the stable neck profile
            // below. Hand pivots stay unpinned because native humanoid variants use different hand
            // locators; the hand-prefix retarget already cancels against each actual hand pivot.
            return pivot(body, 0, 12, 0)
                    && pivot(torso, 0, 24, 0)
                    && pivot(head, 0, 24, 0)
                    && pivot(rightArm, 5, 22, 0)
                    && pivot(leftArm, -5, 22, 0)
                    && pivot(rightLeg, 2, 12, 0)
                    && pivot(leftLeg, -2, 12, 0);
        }

        private static software.bernie.geckolib.cache.object.GeoBone boneOrNull(
                software.bernie.geckolib.cache.object.BakedGeoModel baked, String name) {
            return baked.getBone(name).orElse(null);
        }

        private static boolean pivot(software.bernie.geckolib.cache.object.GeoBone bone,
                                     float x, float y, float z) {
            return Math.abs(bone.getPivotX() - x) < 0.01F
                    && Math.abs(bone.getPivotY() - y) < 0.01F
                    && Math.abs(bone.getPivotZ() - z) < 0.01F;
        }

        private static boolean ownsBone(NativeCastingView view, Object bone) {
            if (!(view instanceof Proxy proxy)
                    || !(bone instanceof software.bernie.geckolib.cache.object.GeoBone geo)) {
                return false;
            }
            return proxy.owns(geo);
        }

        private static void drawBody(PoseStack poseStack, MultiBufferSource buffers, int light,
                                     LivingEntity entity, NativeCastingView view) {
            poseStack.pushPose();
            try {
                // C = native Y-up model frame -> canonical penguin model frame. The body is drawn
                // once before the native traversal; no native cube is mutated.
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(0.0F, -1.5F, 0.0F);
                PenguinBodyLayer.renderCanonicalBody(poseStack, buffers, light, entity,
                        (EntityModel<?>) view);
            } finally {
                poseStack.popPose();
            }
        }

        private static boolean isHeadSubtree(Object bone) {
            if (!(bone instanceof software.bernie.geckolib.cache.object.GeoBone geo)) {
                return true;
            }
            software.bernie.geckolib.cache.object.GeoBone current = geo;
            int guard = 0;
            while (current != null && guard++ < 64) {
                if (HEAD.equals(current.getName())) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }

        private static boolean adjustHead(PoseStack poseStack, Active active, Object bone) {
            if (!(bone instanceof software.bernie.geckolib.cache.object.GeoBone geo)
                    || !HEAD.equals(geo.getName())) {
                return false;
            }
            // D = applyGeoHead(identity) is the canonical head transform in native root space.
            // M maps that root frame to camera space, so the camera-space target is M*D*M^-1.
            // Conjugating by the current parent P applies it before the native head recursion:
            // local = P^-1 * M*D*M^-1 * P. The whole native head subtree and helmet then follow
            // the canonical mount exactly once, including translated/rotated/scaled/inventory
            // renders where the old P^-1*D*P incorrectly used a camera-space D.
            Matrix4f parent = new Matrix4f(poseStack.last().pose());
            PoseStack canonical = new PoseStack();
            PenguinRenderContext.applyGeoHead(canonical);
            Matrix4f d = new Matrix4f(canonical.last().pose());
            Matrix4f m = new Matrix4f(active.rootPose);
            Matrix4f target = new Matrix4f(m).mul(d).mul(new Matrix4f(m).invert());
            Matrix4f local = new Matrix4f(parent).invert().mul(target).mul(parent);
            poseStack.mulPose(local);
            return true;
        }

        private static boolean adjustHand(PoseStack poseStack, Active active, Object bone) {
            if (!(bone instanceof software.bernie.geckolib.cache.object.GeoBone geo)) {
                return false;
            }
            HumanoidArm arm;
            if (RIGHT_HAND.equals(geo.getName())) {
                arm = HumanoidArm.RIGHT;
            } else if (LEFT_HAND.equals(geo.getName())) {
                arm = HumanoidArm.LEFT;
            } else {
                return false;
            }
            PenguinAnimationPose pose = PenguinRenderContext.pose();
            PenguinBodyModel shared = PenguinRenderContext.sharedModel();
            if (pose == null || shared == null) {
                return false;
            }
            PenguinProportions.Profile profile = active.view.kusoPenguin$profile();

            // True penguin grip in canonical renderer blocks. M is the captured native pass root,
            // C is native Y-up -> canonical, and R=scale(-1,-1,1) is the orientation-only basis
            // that turns the grip frame into the native item basis. No vanilla item-tail residual
            // is subtracted: the native BlockAndItemGeoLayer still owns the full item tail.
            Matrix4f target = new Matrix4f(active.rootPose)
                    .scale(-1.0F, -1.0F, 1.0F)
                    .translate(0.0F, -1.5F, 0.0F)
                    .mul(PenguinHeldItemRenderer.gripMatrix(profile, pose, shared, arm))
                    .scale(-1.0F, -1.0F, 1.0F);
            // Preserve the hand's own animated translation and scale once. Its rotation is applied
            // exactly once by the native BlockAndItemGeoLayer tail (translateAndRotateMatrixForBone),
            // so folding it in here would double it. No raw rest pivot or arm length is added.
            target.mul(new Matrix4f()
                    .translate(-geo.getPosX() / 16.0F, geo.getPosY() / 16.0F,
                            geo.getPosZ() / 16.0F)
                    .scale(geo.getScaleX(), geo.getScaleY(), geo.getScaleZ()));

            // Native recursion's tracked prefix, in order and units (blocks):
            // T(-posX,+posY,+posZ)/16 * T(+pivotX,+pivotY,+pivotZ)/16 * RzRyRx * scale.
            // GeoBone pivot already stores the JSON X-negated value, so these are the exact signs
            // used by RenderUtil. The trailing T(-pivot) is deliberately omitted: the native
            // tracker writes the locator before away-from-pivot, which is where the hand item
            // layer's T(+pivot)*rotation tail expects to start. Using prepMatrixForBone here would
            // cancel that trailing term and shift the tracked locator by the hand pivot (~12 px).
            Matrix4f prefix = new Matrix4f()
                    .translate(-geo.getPosX() / 16.0F, geo.getPosY() / 16.0F,
                            geo.getPosZ() / 16.0F)
                    .translate(geo.getPivotX() / 16.0F, geo.getPivotY() / 16.0F,
                            geo.getPivotZ() / 16.0F)
                    .rotate(new Quaternionf().rotationZYX(geo.getRotZ(), geo.getRotY(),
                            geo.getRotX()))
                    .scale(geo.getScaleX(), geo.getScaleY(), geo.getScaleZ());
            Matrix4f parent = new Matrix4f(poseStack.last().pose());
            Matrix4f local = new Matrix4f(parent).invert().mul(target)
                    .mul(new Matrix4f(prefix).invert());
            poseStack.mulPose(local);
            return true;
        }

        private static boolean isSuppressedArmorBone(Object bone) {
            if (!(bone instanceof software.bernie.geckolib.cache.object.GeoBone geo)) {
                return false;
            }
            String name = geo.getName();
            // Keep the native helmet (armorBipedHead) and its descendants; suppress only body
            // armor/cape armor parts that would otherwise draw a second humanoid body.
            return name.startsWith("armorBiped") && !HEAD.equals(name)
                    && !name.equals("armorBipedHead");
        }

        private static final class Proxy extends EntityModel<LivingEntity>
                implements NativeCastingView {
            private final software.bernie.geckolib.cache.object.BakedGeoModel baked;
            private final PenguinProportions.Profile profile = PenguinProportions.maid(0.0F);

            private Proxy(software.bernie.geckolib.cache.object.BakedGeoModel baked) {
                this.baked = baked;
            }

            /** Exact object identity inside this baked model; excludes foreign nested renders. */
            private boolean owns(software.bernie.geckolib.cache.object.GeoBone geo) {
                return baked.getBone(geo.getName()).map(bone -> bone == geo).orElse(false);
            }

            @Override
            public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw, float headPitch) {
                // Native animation already ran once in GeoEntityRenderer.
            }

            @Override
            public void renderToBuffer(PoseStack poseStack,
                                       com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                       int packedLight, int packedOverlay, int color) {
                // The shared body layer draws the penguin mesh; this proxy is never drawn directly.
            }

            @Override
            public boolean kusoPenguin$ready() {
                return true;
            }

            @Override
            public PenguinProportions.Profile kusoPenguin$profile() {
                return profile;
            }

            @Override
            public PenguinPoseAdapter kusoPenguin$adapter() {
                return this::applyAdapter;
            }

            @Override
            public Matrix4f kusoPenguin$headMatrix() {
                return nativeHeadMatrix();
            }

            @Override
            public PenguinAnimationPose kusoPenguin$nativePose(PenguinBodyModel parts,
                                                               PenguinProportions.Profile profile) {
                return buildPose(profile);
            }

            private void applyAdapter(PenguinBodyModel penguin, EntityModel<?> original,
                                      LivingEntity entity) {
                copyBone(baked.getBone(BODY).orElseThrow(), penguin.body());
                // Native anatomical right_arm is negative X and maps to historical left_wing.
                copyBone(baked.getBone(LEFT_ARM).orElseThrow(), penguin.rightWing());
                copyBone(baked.getBone(RIGHT_ARM).orElseThrow(), penguin.leftWing());
                copyBone(baked.getBone(RIGHT_LEG).orElseThrow(), penguin.rightFoot());
                copyBone(baked.getBone(LEFT_LEG).orElseThrow(), penguin.leftFoot());
            }

            private static void copyBone(
                    software.bernie.geckolib.cache.object.GeoBone bone, ModelPart target) {
                var initial = target.getInitialPose();
                target.x = initial.x + bone.getPosX();
                target.y = initial.y - bone.getPosY();
                target.z = initial.z + bone.getPosZ();
                target.xRot = -bone.getRotX();
                target.yRot = -bone.getRotY();
                target.zRot = bone.getRotZ();
                target.xScale = bone.getScaleX();
                target.yScale = bone.getScaleY();
                target.zScale = bone.getScaleZ();
            }

            private PenguinAnimationPose buildPose(PenguinProportions.Profile profile) {
                PenguinAnimationClip bind = PenguinMeshModel.animationClip();
                if (bind == null) {
                    return null;
                }
                software.bernie.geckolib.cache.object.GeoBone body = bone(BODY);
                software.bernie.geckolib.cache.object.GeoBone torso = bone(TORSO);
                software.bernie.geckolib.cache.object.GeoBone head = bone(HEAD);
                software.bernie.geckolib.cache.object.GeoBone rightArm = bone(RIGHT_ARM);
                software.bernie.geckolib.cache.object.GeoBone leftArm = bone(LEFT_ARM);
                software.bernie.geckolib.cache.object.GeoBone rightLeg = bone(RIGHT_LEG);
                software.bernie.geckolib.cache.object.GeoBone leftLeg = bone(LEFT_LEG);

                // Native hierarchy has body as the parent of torso/head/arms/legs. World matrices
                // are absolute and use the shared bind pivots, never the long native limb lengths.
                Matrix4f pelvis = absolute(bind.bind(PenguinRig.PELVIS), body);
                Matrix4f chest = child(pelvis, bind.bind(PenguinRig.CHEST)
                        .sub(bind.bind(PenguinRig.PELVIS)), torso, true);
                Matrix4f headWorld = child(pelvis, bind.bind(PenguinRig.HEAD)
                        .sub(bind.bind(PenguinRig.PELVIS)), head, false);
                Matrix4f[] world = new Matrix4f[PenguinRig.COUNT];
                world[PenguinRig.ROOT] = new Matrix4f();
                world[PenguinRig.PELVIS] = pelvis;
                world[PenguinRig.CHEST] = chest;
                world[PenguinRig.HEAD] = headWorld;
                // Native anatomical right_arm is negative X and maps to left_wing; native
                // left_arm is positive X and maps to right_wing, matching applyAdapter above.
                wing(world, bind, pelvis, rightArm, PenguinRig.LEFT_WING);
                wing(world, bind, pelvis, leftArm, PenguinRig.RIGHT_WING);
                leg(world, bind, pelvis, rightLeg, PenguinRig.RIGHT_HIP);
                leg(world, bind, pelvis, leftLeg, PenguinRig.LEFT_HIP);

                Matrix4f nativeHead = nativeHeadMatrix();
                Quaternionf headRotation = headWorld.getUnnormalizedRotation(new Quaternionf())
                        .normalize();
                Vector3f headBlocks = headWorld.getTranslation(new Vector3f())
                        .mul(profile.bodyScale() / 16.0F)
                        .add(0.0F, profile.bodyOffsetBlocks(), 0.0F);
                Matrix4f headDelta = new Matrix4f().translation(headBlocks)
                        .rotate(headRotation)
                        .mul(new Matrix4f(nativeHead).invert());
                return PenguinRenderContext.fromNativeRig(world, headDelta, bind);
            }

            private static Matrix4f absolute(Vector3f bind, software.bernie.geckolib.cache.object.GeoBone bone) {
                // Canonical position delta is (+nativeX, -nativeY, +nativeZ) pixels; rotations are
                // (-X,-Y,+Z); scale is copied. Both are already final after native animations.
                return new Matrix4f().translation(new Vector3f(bind).add(position(bone)))
                        .rotate(rotation(bone)).scale(scale(bone));
            }

            private static Matrix4f child(Matrix4f parent, Vector3f bindOffset,
                                          software.bernie.geckolib.cache.object.GeoBone bone,
                                          boolean scale) {
                Matrix4f matrix = new Matrix4f(parent)
                        .translate(new Vector3f(bindOffset).add(position(bone)))
                        .rotate(rotation(bone));
                return scale ? matrix.scale(scale(bone)) : matrix;
            }

            private static void wing(Matrix4f[] world, PenguinAnimationClip bind, Matrix4f pelvis,
                                     software.bernie.geckolib.cache.object.GeoBone arm, int root) {
                int mid = root + 1;
                int tip = root + 2;
                world[root] = child(pelvis, bind.bind(root).sub(bind.bind(PenguinRig.PELVIS)),
                        arm, true);
                // No source middle/tip bones: follow the existing bind segment lengths neutrally.
                world[mid] = new Matrix4f(world[root])
                        .translate(bind.bind(mid).sub(bind.bind(root)));
                world[tip] = new Matrix4f(world[mid])
                        .translate(bind.bind(tip).sub(bind.bind(mid)));
            }

            private static void leg(Matrix4f[] world, PenguinAnimationClip bind, Matrix4f pelvis,
                                    software.bernie.geckolib.cache.object.GeoBone leg, int hip) {
                int knee = hip + 1;
                int foot = hip + 2;
                world[hip] = child(pelvis, bind.bind(hip).sub(bind.bind(PenguinRig.PELVIS)),
                        leg, true);
                world[knee] = new Matrix4f(world[hip])
                        .translate(bind.bind(knee).sub(bind.bind(hip)));
                world[foot] = new Matrix4f(world[knee])
                        .translate(bind.bind(foot).sub(bind.bind(knee)));
            }

            private static Vector3f position(software.bernie.geckolib.cache.object.GeoBone bone) {
                return new Vector3f(bone.getPosX(), -bone.getPosY(), bone.getPosZ());
            }

            private static Quaternionf rotation(software.bernie.geckolib.cache.object.GeoBone bone) {
                return new Quaternionf().rotationZYX(bone.getRotZ(), -bone.getRotY(),
                        -bone.getRotX());
            }

            private static Vector3f scale(software.bernie.geckolib.cache.object.GeoBone bone) {
                return new Vector3f(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
            }

            private software.bernie.geckolib.cache.object.GeoBone bone(String name) {
                return baked.getBone(name).orElseThrow();
            }

            /**
             * Native head locator converted to the canonical penguin frame:
             * {@code Tdrop * C^-1 * rigidLocator * R}. Scale is stripped so a non-unit native head
             * bone cannot shear the rigid penguin head; {@code R=scale(-1,-1,1)} is orientation
             * only and turns the neutral Y-up head quaternion into the canonical Y-down basis.
             */
            private Matrix4f nativeHeadMatrix() {
                software.bernie.geckolib.cache.object.GeoBone head = bone(HEAD);
                List<software.bernie.geckolib.cache.object.GeoBone> chain = new ArrayList<>();
                software.bernie.geckolib.cache.object.GeoBone current = head;
                int guard = 0;
                while (current != null && guard++ < 64) {
                    chain.add(current);
                    current = current.getParent();
                }
                Collections.reverse(chain);
                PoseStack stack = new PoseStack();
                for (int i = 0; i < chain.size() - 1; i++) {
                    software.bernie.geckolib.util.RenderUtil.prepMatrixForBone(stack, chain.get(i));
                }
                software.bernie.geckolib.util.RenderUtil.translateMatrixToBone(stack, head);
                software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(stack, head);
                software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundBone(stack, head);
                software.bernie.geckolib.util.RenderUtil.scaleMatrixForBone(stack, head);
                Matrix4f locator = new Matrix4f(stack.last().pose());
                Vector3f translation = locator.getTranslation(new Vector3f());
                Quaternionf rotation = locator.getUnnormalizedRotation(new Quaternionf());
                if (rotation.lengthSquared() < 1.0E-8F) {
                    rotation.identity();
                } else {
                    rotation.normalize();
                }
                Matrix4f rigidLocator = new Matrix4f().translation(translation).rotate(rotation);
                Matrix4f inverseC = new Matrix4f().translate(0.0F, 1.5F, 0.0F)
                        .scale(-1.0F, -1.0F, 1.0F);
                Matrix4f orientationBasis = new Matrix4f().scale(-1.0F, -1.0F, 1.0F);
                return new Matrix4f().translate(0.0F, profile.headDropBlocks(), 0.0F)
                        .mul(inverseC).mul(rigidLocator).mul(orientationBasis);
            }
        }
    }
}
