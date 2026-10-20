package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;

/**
 * Draws only the original head (including hat/nose/head children) for a supported entity.
 * The caller supplies the original vertex consumer, light, overlay and color.
 */
public final class PenguinHeadRenderer {
    private PenguinHeadRenderer() {
    }

    public static boolean renderHead(EntityModel<?> model, PoseStack poseStack,
                                     VertexConsumer buffer, int packedLight,
                                     int packedOverlay, int color) {
        PenguinProportions.Profile profile = PenguinProportions.forModel(model);
        if (profile == null) {
            return false;
        }
        // The head keeps its size and only moves down onto the scaled body. Villager profession
        // hats reach this helper through RenderLayerMixin, so they get the same single shift.
        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, profile.headDropBlocks(), 0.0F);
            if (model instanceof HumanoidModel<?> humanoid) {
                humanoid.head.render(poseStack, buffer, packedLight, packedOverlay, color);
                humanoid.hat.render(poseStack, buffer, packedLight, packedOverlay, color);
            } else {
                ((VillagerModel<?>) model).getHead()
                        .render(poseStack, buffer, packedLight, packedOverlay, color);
            }
            return true;
        } finally {
            poseStack.popPose();
        }
    }
}
