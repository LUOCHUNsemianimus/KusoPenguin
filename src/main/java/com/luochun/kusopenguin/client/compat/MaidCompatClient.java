package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.client.PenguinBodyModel;
import com.luochun.kusopenguin.client.PenguinPoseAdapter;
import com.luochun.kusopenguin.client.PenguinProportions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Optional Touhou Little Maid bridge.
 *
 * <p>The outer facade contains no maid API types in its own signatures. The nested implementation
 * is resolved only after {@link ModList} confirms the mod is present, so a standalone game or a
 * dedicated server never loads maid classes. If any expected Bedrock bone is missing, every method
 * returns the neutral value and the caller keeps the complete native maid renderer.
 */
public final class MaidCompatClient {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private MaidCompatClient() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isMaidModel(Object model) {
        return AVAILABLE && Impl.isMaidModel(model);
    }

    public static boolean isReady(Object model) {
        return AVAILABLE && Impl.isReady(model);
    }

    public static PenguinProportions.Profile maidProfile(Object model) {
        return AVAILABLE ? Impl.profile(model) : null;
    }

    public static PenguinPoseAdapter adapter(Object model) {
        return AVAILABLE ? Impl.adapter(model) : null;
    }

    public static Matrix4f headMatrix(Object model, float headDropBlocks) {
        return AVAILABLE ? Impl.headMatrix(model, headDropBlocks) : null;
    }

    public static float headMaxY(Object model, Matrix4f delta, float headDropBlocks) {
        return AVAILABLE ? Impl.headMaxY(model, delta, headDropBlocks) : Float.NaN;
    }

    public static boolean renderHeadSubtree(Object model, PoseStack poseStack, VertexConsumer buffer,
                                            int packedLight, int packedOverlay, int color) {
        return AVAILABLE && Impl.renderHeadSubtree(model, poseStack, buffer, packedLight,
                packedOverlay, color);
    }

    /** Applies the head's native ancestor chain so a maid layer can add its local head transform. */
    public static void applyHeadAncestors(Object model, PoseStack poseStack) {
        if (AVAILABLE) {
            Impl.applyHeadAncestors(model, poseStack);
        }
    }

    public static boolean isHeadLayer(Object layer) {
        return AVAILABLE && Impl.isHeadLayer(layer);
    }

    public static boolean isBodyDecorationLayer(Object layer) {
        return AVAILABLE && Impl.isBodyDecorationLayer(layer);
    }

    public static boolean isHeldItemLayer(Object layer) {
        return AVAILABLE && Impl.isHeldItemLayer(layer);
    }

    /**
     * Lazy implementation. This class file is not initialized until one of the facade methods above
     * is called while the maid mod is loaded.
     */
    private static final class Impl {
        private static final String HEAD = "head";
        private static final String BODY = "body";
        private static final String ARM_LEFT = "armLeft";
        private static final String ARM_RIGHT = "armRight";
        private static final String LEG_LEFT = "legLeft";
        private static final String LEG_RIGHT = "legRight";

        private Impl() {
        }

        static boolean isMaidModel(Object model) {
            return model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?>;
        }

        static boolean isReady(Object model) {
            if (!(model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock)) {
                return false;
            }
            return isReady(bedrock);
        }

        private static boolean isReady(
                com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> model) {
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart head =
                    model.hasHead() ? model.getHead() : null;
            if (head == null || !head.visible) {
                return false;
            }
            Map<String, com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> bones =
                    model.getModelMap();
            return bones.get(BODY) != null
                    && bones.get(ARM_LEFT) != null
                    && bones.get(ARM_RIGHT) != null
                    && bones.get(LEG_LEFT) != null
                    && bones.get(LEG_RIGHT) != null;
        }

        static PenguinProportions.Profile profile(Object model) {
            if (!(model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock)
                    || !isReady(bedrock)) {
                return null;
            }
            return PenguinProportions.maid(restNeckY(bedrock));
        }

        /**
         * Stable rest position of the native head pivot in the Java Bedrock frame, in model pixels.
         * Only bind pivots and initial rotations are read; animated offsets, rotations and scales
         * are deliberately ignored so the profile cannot resize or jitter while the head looks
         * around. A root {@code head} bone with JSON pivot Y 18 therefore converts to Java Y 6.
         */
        private static float restNeckY(
                com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> model) {
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart head =
                    model.getHead();
            Vector3f pivot = new Vector3f(head.x, head.y, head.z);
            Quaternionf rotation = new Quaternionf();
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart parent =
                    head.getParent();
            int guard = 0;
            while (parent != null && guard++ < 64) {
                rotation.identity().rotateZYX(parent.initRotZ, parent.initRotY, parent.initRotX);
                rotation.transform(pivot);
                pivot.add(parent.x, parent.y, parent.z);
                parent = parent.getParent();
            }
            return pivot.y;
        }

        static PenguinPoseAdapter adapter(Object model) {
            if (!isReady(model)) {
                return null;
            }
            return Impl::applyPose;
        }

        private static void applyPose(PenguinBodyModel penguin,
                                      net.minecraft.client.model.EntityModel<?> original,
                                      net.minecraft.world.entity.LivingEntity entity) {
            if (!(original instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> model)
                    || !isReady(model)) {
                return;
            }
            Map<String, com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> bones =
                    model.getModelMap();
            // Historical wing names use the opposite X side from Bedrock's anatomical arm names,
            // matching the existing humanoid adapter contract.
            copyAnimated(bones.get(BODY), penguin.body());
            copyAnimated(bones.get(ARM_LEFT), penguin.rightWing());
            copyAnimated(bones.get(ARM_RIGHT), penguin.leftWing());
            copyAnimated(bones.get(LEG_LEFT), penguin.leftFoot());
            copyAnimated(bones.get(LEG_RIGHT), penguin.rightFoot());
        }

        private static void copyAnimated(
                com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart source,
                ModelPart target) {
            if (source == null || target == null) {
                return;
            }
            PartPose initial = target.getInitialPose();
            // Bedrock animations use offsetX/Y/Z in blocks; ModelPart pivots stay in model pixels.
            target.x = initial.x + source.offsetX * 16.0F;
            target.y = initial.y + source.offsetY * 16.0F;
            target.z = initial.z + source.offsetZ * 16.0F;
            target.xRot = source.xRot;
            target.yRot = source.yRot;
            target.zRot = source.zRot;
            target.xScale = source.xScale;
            target.yScale = source.yScale;
            target.zScale = source.zScale;
            target.visible = source.visible;
        }

        static Matrix4f headMatrix(Object model, float headDropBlocks) {
            if (!(model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock)
                    || !isReady(bedrock)) {
                return null;
            }
            PoseStack stack = new PoseStack();
            stack.translate(0.0F, headDropBlocks, 0.0F);
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart head = bedrock.getHead();
            applyAncestors(head, stack);
            head.translateAndRotateAndScale(stack);
            return new Matrix4f(stack.last().pose());
        }

        static float headMaxY(Object model, Matrix4f delta, float headDropBlocks) {
            if (!(model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock)
                    || !isReady(bedrock) || delta == null) {
                return Float.NaN;
            }
            MaxYConsumer collector = new MaxYConsumer();
            PoseStack stack = new PoseStack();
            stack.mulPose(delta);
            stack.translate(0.0F, headDropBlocks, 0.0F);
            for (com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart part
                    : headParts(bedrock)) {
                stack.pushPose();
                applyAncestors(part, stack);
                part.render(stack, collector, 0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
                stack.popPose();
            }
            return collector.maxY == Float.NEGATIVE_INFINITY ? Float.NaN : collector.maxY;
        }

        static boolean renderHeadSubtree(Object model, PoseStack poseStack, VertexConsumer buffer,
                                         int packedLight, int packedOverlay, int color) {
            if (!(model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock)
                    || !isReady(bedrock)) {
                return false;
            }
            float alpha = ((color >> 24) & 0xFF) / 255.0F;
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            for (com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart part
                    : headParts(bedrock)) {
                poseStack.pushPose();
                applyAncestors(part, poseStack);
                // Actual SimpleBedrockModel signature: float color components, not a packed int.
                part.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                poseStack.popPose();
            }
            return true;
        }

        static void applyHeadAncestors(Object model, PoseStack poseStack) {
            if (model instanceof com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> bedrock
                    && isReady(bedrock)) {
                applyAncestors(bedrock.getHead(), poseStack);
            }
        }

        static boolean isHeadLayer(Object layer) {
            return layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBipedHead;
        }

        static boolean isBodyDecorationLayer(Object layer) {
            return layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBackpack
                    || layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBackItem
                    || layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBanner;
        }

        static boolean isHeldItemLayer(Object layer) {
            return layer instanceof com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidHeldItem;
        }

        private static List<com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> headParts(
                com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel<?> model) {
            List<com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> parts =
                    new ArrayList<>();
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart head = model.getHead();
            parts.add(head);
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart parent = head.getParent();
            if (parent != null) {
                for (Map.Entry<String, com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> entry
                        : model.getModelMap().entrySet()) {
                    if (entry.getValue() != head && entry.getValue().getParent() == parent
                            && isHeadDecoration(entry.getKey())) {
                        parts.add(entry.getValue());
                    }
                }
            }
            return parts;
        }

        private static boolean isHeadDecoration(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.startsWith("hat") || lower.startsWith("hair") || lower.startsWith("headwear")
                    || lower.startsWith("ahoge") || lower.startsWith("ribbon") || lower.startsWith("bow")
                    || lower.startsWith("horn");
        }

        private static void applyAncestors(
                com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart part,
                PoseStack poseStack) {
            List<com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> chain =
                    new ArrayList<>();
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart current =
                    part.getParent();
            int guard = 0;
            while (current != null && guard++ < 64) {
                chain.add(current);
                current = current.getParent();
            }
            for (int i = chain.size() - 1; i >= 0; i--) {
                chain.get(i).translateAndRotateAndScale(poseStack);
            }
        }

        private static final class MaxYConsumer implements VertexConsumer {
            private float maxY = Float.NEGATIVE_INFINITY;

            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                if (y > maxY) {
                    maxY = y;
                }
                return this;
            }

            @Override
            public VertexConsumer setColor(int red, int green, int blue, int alpha) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        }
    }
}
