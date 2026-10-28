package com.luochun.kusopenguin.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Immutable set of retained native terminals for one render pass.
 *
 * <p>Each terminal is replayed by walking its complete ancestor chain with
 * {@link ModelPart#translateAndRotate}. Every ancestor's visibility is honoured, so native
 * {@code anchor2.visible = isSleep()} and weapon/ornament switches keep their own policy. Only the
 * terminal is rendered; its native children remain attached, which preserves horns, eye/hair,
 * anchor chains, snake heads and trails without drawing a second penguin body.
 */
final class NativeRetainedGeometry {
    private final NativeModelPartRig.JointPath[] terminals;

    private NativeRetainedGeometry(NativeModelPartRig.JointPath[] terminals) {
        this.terminals = terminals;
    }

    /**
     * Resolves exact root-to-terminal paths. Any missing path returns null so the caller can keep
     * the whole native draw instead of silently hiding part of a different model version.
     */
    static NativeRetainedGeometry resolve(ModelPart root, String[]... paths) {
        if (root == null || paths == null) {
            return null;
        }
        NativeModelPartRig.JointPath[] terminals =
                new NativeModelPartRig.JointPath[paths.length];
        for (int i = 0; i < paths.length; i++) {
            terminals[i] = paths[i] == null ? null : NativeModelPartRig.path(root, paths[i]);
            if (terminals[i] == null) {
                return null;
            }
        }
        return new NativeRetainedGeometry(terminals);
    }

    boolean isEmpty() {
        return terminals.length == 0;
    }

    /** Replays the retained terminals into the caller-provided native buffer and color. */
    void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                int color) {
        for (NativeModelPartRig.JointPath path : terminals) {
            ModelPart[] chain = path.chain();
            boolean visible = true;
            for (ModelPart part : chain) {
                if (!part.visible) {
                    visible = false;
                    break;
                }
            }
            if (!visible) {
                continue;
            }
            poseStack.pushPose();
            try {
                for (int i = 0; i < chain.length - 1; i++) {
                    chain[i].translateAndRotate(poseStack);
                }
                chain[chain.length - 1].render(poseStack, buffer, packedLight, packedOverlay,
                        color);
            } finally {
                poseStack.popPose();
            }
        }
    }
}
