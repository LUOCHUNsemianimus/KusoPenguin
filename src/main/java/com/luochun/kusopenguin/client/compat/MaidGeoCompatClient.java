package com.luochun.kusopenguin.client.compat;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional Touhou Little Maid Gecko-model bridge.
 *
 * <p>As with {@link MaidCompatClient}, the outer facade has no maid API types in its signatures.
 * The nested implementation is only initialized after the maid mod is present, so standalone and
 * dedicated-server runs never resolve GeckoLib classes.
 *
 * <p>Coordinate contract (readable summary, all values are model pixels unless a name says
 * {@code Blocks}):
 * <ul>
 *   <li>Native Gecko geometry is Y-up with its floor at Y=0.</li>
 *   <li>The shared penguin body is authored Y-down with its floor at Y=24, which is exactly the
 *       vanilla living-model flip: {@code scale(-1,-1,1)} then {@code translate(0,-1.5,0)}.</li>
 *   <li>That flip is the canonical-to-Gecko transform C. Its explicit inverse converts native
 *       head transforms to the canonical frame; the body is drawn under C.</li>
 *   <li>Local Gecko rotations conjugate through C: X and Y angles change sign, Z stays.</li>
 * </ul>
 */
public final class MaidGeoCompatClient {
    private MaidGeoCompatClient() {
    }

    public static EntityModel<?> createView(LivingEntity entity, Object animatable) {
        return entity != null && PenguinEntitySupport.isMaid(entity.getType())
                && MaidCompatClient.isAvailable() ? Impl.createView(entity, animatable) : null;
    }

    public static boolean isReplacement(Object model, LivingEntity entity) {
        return entity != null && PenguinEntitySupport.isMaid(entity.getType())
                && MaidCompatClient.isAvailable() && Impl.isReplacement(model, entity);
    }

    /** Native Gecko head-gear layer (helmet/skull), not a backpack/banner layer. */
    public static boolean isGeoHeadLayer(Object layer) {
        return MaidCompatClient.isAvailable() && Impl.isGeoHeadLayer(layer);
    }

    /** Backpack/back item/banner geometry authored for the native Gecko body. */
    public static boolean isGeoDecorationLayer(Object layer) {
        return MaidCompatClient.isAvailable() && Impl.isGeoDecorationLayer(layer);
    }

    public static void renderReplacement(Object view, Object renderer, Object animatedModel,
                                         LivingEntity entity, PoseStack poseStack,
                                         MultiBufferSource bufferSource, RenderType renderType,
                                         VertexConsumer fallbackBuffer, int packedLight,
                                         int packedOverlay, float red, float green, float blue,
                                         float alpha) {
        if (MaidCompatClient.isAvailable()) {
            Impl.renderReplacement(view, renderer, animatedModel, entity, poseStack, bufferSource,
                    renderType, fallbackBuffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    private static final class Impl {
        private static final String HEAD = "Head";
        private static final String ALL_HEAD = "AllHead";
        private static final String HAT = "Hat";

        private Impl() {
        }

        static EntityModel<?> createView(LivingEntity entity, Object animatable) {
            if (!(animatable instanceof com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> a)) {
                return null;
            }
            return new GeoView(entity, a);
        }

        static boolean isReplacement(Object model, LivingEntity entity) {
            // Contract is the public GeoMaidView, not the private Gecko view: the YSM proxy and the
            // Gecko proxy share the same profile/head/adapter and layer gating.
            if (!(model instanceof GeoMaidView view) || !view.kusoPenguin$ready()) {
                return false;
            }
            if (!PenguinRenderSupport.shouldReplace(entity)) {
                return false;
            }
            // A detached death draw needs native head bounds and a different lift policy. Keep the
            // complete native Gecko death renderer; the shared penguin death path is unchanged.
            if (PenguinDeathClient.rendering(entity)) {
                return false;
            }
            return view.kusoPenguin$profile() != null;
        }

        static void renderReplacement(Object viewObject, Object rendererObject, Object modelObject,
                                      LivingEntity entity, PoseStack poseStack,
                                      MultiBufferSource bufferSource, RenderType renderType,
                                      VertexConsumer fallbackBuffer, int packedLight,
                                      int packedOverlay, float red, float green, float blue,
                                      float alpha) {
            GeoView view = (GeoView) viewObject;
            IGeoRenderer renderer = (IGeoRenderer) rendererObject;
            AnimatedGeoModel model = (AnimatedGeoModel) modelObject;
            // The interface default render would draw every native bone. Draw only the shared body
            // here, then the native head subtree. No temporary bone flags/scales are mutated, so
            // nested/shared-model draws and exceptions cannot leak visibility state.
            if (bufferSource != null) {
                poseStack.pushPose();
                try {
                    // Canonical penguin frame (Y-down, floor 24) -> native Gecko frame.
                    // C = scale(-1,-1,1) then translate(0,-1.5,0).
                    poseStack.scale(-1.0F, -1.0F, 1.0F);
                    poseStack.translate(0.0F, -1.5F, 0.0F);
                    PenguinBodyLayer.renderCanonicalBody(poseStack, bufferSource, packedLight,
                            entity, view);
                } finally {
                    poseStack.popPose();
                }
            }

            VertexConsumer buffer = bufferSource == null
                    ? fallbackBuffer : bufferSource.getBuffer(renderType);
            poseStack.pushPose();
            try {
                // S * C * Delta * Tdrop * C^-1, then the real native head ancestor chain.
                // applyGeoHead owns the explicit canonical/native conjugation and head drop.
                PenguinRenderContext.applyGeoHead(poseStack);
                renderHead(model, renderer, poseStack, buffer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            } finally {
                poseStack.popPose();
            }
        }

        private static void renderHead(AnimatedGeoModel model, IGeoRenderer renderer,
                                       PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                       int packedOverlay, float red, float green, float blue,
                                       float alpha) {
            AnimatedGeoBone headRoot = geometryRoot(model);
            if (headRoot == null) {
                return;
            }
            // The head ancestor chain is scoped to the head. The separate Hat must not inherit
            // those ancestors twice, so each subtree gets its own push/finally-pop.
            renderSubtree(model, headRoot, renderer, poseStack, buffer, packedLight, packedOverlay,
                    red, green, blue, alpha);

            AnimatedGeoBone hat = findBone(model, HAT);
            if (hat != null && !isDescendant(headRoot, hat)) {
                renderSubtree(model, hat, renderer, poseStack, buffer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            }
        }

        private static void renderSubtree(AnimatedGeoModel model, AnimatedGeoBone root,
                                          IGeoRenderer renderer, PoseStack poseStack,
                                          VertexConsumer buffer, int packedLight, int packedOverlay,
                                          float red, float green, float blue, float alpha) {
            List<AnimatedGeoBone> chain = chainTo(model, root);
            poseStack.pushPose();
            try {
                for (int i = 0; i < chain.size() - 1; i++) {
                    RenderUtils.prepMatrixForBone(poseStack, chain.get(i));
                }
                renderer.renderRecursively(root, poseStack, buffer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            } finally {
                poseStack.popPose();
            }
        }

        static boolean isGeoHeadLayer(Object layer) {
            return layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBipedHead;
        }

        static boolean isGeoDecorationLayer(Object layer) {
            return layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBackpack
                    || layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBackItem
                    || layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBanner;
        }

        /**
         * Native head geometry root. AllHead retains the full hair/ear geometry when it is the
         * ancestor of the standard Head locator; otherwise the real Head subtree is used. The
         * separate Hat sibling is drawn by {@link #renderHead} and is never cut by height.
         */
        private static AnimatedGeoBone geometryRoot(AnimatedGeoModel model) {
            AnimatedGeoBone allHead = findBone(model, ALL_HEAD);
            AnimatedGeoBone head = findBone(model, HEAD);
            if (allHead != null && head != null && isDescendant(allHead, head)) {
                return allHead;
            }
            return head;
        }

        private static AnimatedGeoBone findBone(AnimatedGeoModel model, String name) {
            AnimatedGeoBone direct = model.bones().get(name);
            if (direct != null) {
                return direct;
            }
            for (Map.Entry<String, AnimatedGeoBone> entry : model.bones().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private static boolean isDescendant(AnimatedGeoBone ancestor, AnimatedGeoBone candidate) {
            if (ancestor == candidate) {
                return true;
            }
            for (AnimatedGeoBone child : ancestor.children()) {
                if (isDescendant(child, candidate)) {
                    return true;
                }
            }
            return false;
        }

        /** Standard Head locator chain; AllHead remains a geometry-only alias. */
        private static List<AnimatedGeoBone> headLocatorChain(AnimatedGeoModel model) {
            AnimatedGeoBone head = findBone(model, HEAD);
            return head == null ? Collections.emptyList() : chainTo(model, head);
        }

        private static List<AnimatedGeoBone> chainTo(AnimatedGeoModel model, AnimatedGeoBone bone) {
            List<AnimatedGeoBone> chain = new ArrayList<>();
            AnimatedGeoBone current = bone;
            int guard = 0;
            while (current != null && guard++ < 64) {
                chain.add(current);
                GeoBone parent = current.geoBone().parent();
                current = parent == null ? null : model.bones().get(parent.name());
            }
            Collections.reverse(chain);
            return chain;
        }

        private static AnimatedGeoBone parentOf(AnimatedGeoModel model, AnimatedGeoBone bone) {
            GeoBone parent = bone.geoBone().parent();
            return parent == null ? null : model.bones().get(parent.name());
        }

        /**
         * Head matrix expected by the shared pose helpers:
         * {@code Tdrop * C^-1 * HeadLocator * orientationBasis}.
         *
         * <p>{@code RenderUtils.prepMatrixForLocator} keeps the last (Head) pivot, unlike
         * {@code prepMatrixForBone}, whose pivot cancels at rest. The locator matrix is reduced to
         * a rigid translation+rotation so a native non-unit bone scale cannot shear the shared
         * head. {@code C^-1 = translate(0,1.5,0) * scale(-1,-1,1)} is also the proper 180-degree
         * Z basis that maps native Y-up axes onto the canonical Y-down penguin frame.
         */
        static Matrix4f headMatrix(AnimatedGeoModel model, float headDropBlocks) {
            List<AnimatedGeoBone> chain = headLocatorChain(model);
            if (chain.isEmpty()) {
                return new Matrix4f();
            }
            PoseStack stack = new PoseStack();
            RenderUtils.prepMatrixForLocator(stack, chain);
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
            // Tdrop is applied in canonical model space after the native->canonical conversion.
            // Right-multiplying R = scale(-1,-1,1) (orientation only, no local translation) turns
            // the neutral native Y-up head quaternion into identity in the canonical Y-down frame.
            Matrix4f orientationBasis = new Matrix4f().scale(-1.0F, -1.0F, 1.0F);
            return new Matrix4f().translate(0.0F, headDropBlocks, 0.0F).mul(inverseC)
                    .mul(rigidLocator).mul(orientationBasis);
        }

        /**
         * Stable bind neck in canonical model pixels. The head pivot is transformed by the
         * ancestors' initial rotations only; animated offsets and rotations are ignored so the
         * profile cannot resize or jitter while the head looks around.
         */
        static float restNeckY(AnimatedGeoModel model) {
            AnimatedGeoBone head = findBone(model, HEAD);
            if (head == null) {
                return Float.NaN;
            }
            Vector3f point = new Vector3f(head.getPivotX(), head.getPivotY(), head.getPivotZ());
            AnimatedGeoBone parent = parentOf(model, head);
            int guard = 0;
            while (parent != null && guard++ < 64) {
                BoneSnapshot snapshot = parent.getInitialSnapshot();
                Vector3f pivot = new Vector3f(parent.getPivotX(), parent.getPivotY(),
                        parent.getPivotZ());
                Quaternionf rotation = new Quaternionf().rotateZYX(snapshot.rotationValueZ,
                        snapshot.rotationValueY, snapshot.rotationValueX);
                point.sub(pivot);
                rotation.transform(point);
                point.add(pivot);
                // Gecko animation position offsets are already model pixels; RenderUtils divides
                // them by 16 only when writing them into the native pose stack.
                point.add(snapshot.positionOffsetX, snapshot.positionOffsetY,
                        snapshot.positionOffsetZ);
                parent = parentOf(model, parent);
            }
            // Gecko Y-up floor 0 -> canonical Y-down floor 24.
            return 24.0F - point.y;
        }

        /**
         * Copies native Gecko bone animation into the shared penguin parts.
         *
         * <p>GeoBuilder already negates JSON X in the native bind, so the anatomical mapping is
         * {@code RightArm -> rightWing}, {@code LeftArm -> leftWing}, and likewise for the legs.
         * Rotations are conjugated by C (X/Y signs invert, Z keeps its sign). Position offsets are
         * model pixels, not blocks: native {@code -positionX/16} becomes canonical
         * {@code +positionX}, while Y/Z keep their C^-1 signs. Missing limbs deliberately keep the
         * neutral penguin bind pose.
         */
        static void applyGeoPose(PenguinBodyModel penguin, EntityModel<?> original,
                                 LivingEntity entity) {
            if (!(original instanceof GeoView view)) {
                return;
            }
            AnimatedGeoModel model = view.resolve();
            if (model == null) {
                return;
            }
            Map<String, AnimatedGeoBone> bones = model.bones();
            copyAnimated(bones.get("Body"), penguin.body());
            copyAnimated(bones.get("RightArm"), penguin.rightWing());
            copyAnimated(bones.get("LeftArm"), penguin.leftWing());
            copyAnimated(bones.get("RightLeg"), penguin.rightFoot());
            copyAnimated(bones.get("LeftLeg"), penguin.leftFoot());
        }

        private static void copyAnimated(AnimatedGeoBone source, ModelPart target) {
            if (source == null || target == null) {
                return;
            }
            PartPose initial = target.getInitialPose();
            // Preserve the penguin bind offset; only the animated delta is copied.
            target.x = initial.x + source.getPositionX();
            target.y = initial.y - source.getPositionY();
            target.z = initial.z + source.getPositionZ();
            target.xRot = -source.getRotationX();
            target.yRot = -source.getRotationY();
            target.zRot = source.getRotationZ();
            target.xScale = source.getScaleX();
            target.yScale = source.getScaleY();
            target.zScale = source.getScaleZ();
            target.visible = !source.isHidden();
        }

        /**
         * Lightweight EntityModel view. It stores the animatable rather than copying evaluated
         * matrices, so every helper sees the one current animation snapshot of this frame. No
         * second pose evaluator exists here.
         */
        private static final class GeoView extends EntityModel<LivingEntity> implements GeoMaidView {
            private final LivingEntity entity;
            private final com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> animatable;

            private GeoView(LivingEntity entity,
                            com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> animatable) {
                this.entity = entity;
                this.animatable = animatable;
            }

            private AnimatedGeoModel resolve() {
                return this.animatable.getCurrentModel();
            }

            @Override
            public boolean kusoPenguin$ready() {
                AnimatedGeoModel model = resolve();
                return model != null && geometryRoot(model) != null
                        && findBone(model, HEAD) != null;
            }

            @Override
            public PenguinProportions.Profile kusoPenguin$profile() {
                AnimatedGeoModel model = resolve();
                if (model == null || !kusoPenguin$ready()) {
                    return null;
                }
                float neck = restNeckY(model);
                return Float.isNaN(neck) ? null : PenguinProportions.maid(neck);
            }

            @Override
            public PenguinPoseAdapter kusoPenguin$adapter() {
                return kusoPenguin$ready() ? Impl::applyGeoPose : null;
            }

            @Override
            public Matrix4f kusoPenguin$headMatrix() {
                AnimatedGeoModel model = resolve();
                PenguinProportions.Profile profile = kusoPenguin$profile();
                return model == null || profile == null ? new Matrix4f()
                        : headMatrix(model, profile.headDropBlocks());
            }

            @Override
            public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw, float headPitch) {
                // The native animatable already owns this entity's animation state.
            }

            @Override
            public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                       int packedOverlay, int color) {
                // Never used as a standalone model; the Gecko renderer's head/body pass owns it.
            }
        }
    }
}
