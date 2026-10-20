package com.luochun.kusopenguin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;

/**
 * The single source of truth for the chibi preview proportions.
 *
 * <p>The original model keeps its head size. Only the headless body is scaled, around the
 * original foot plane {@code Y = 24}, and the head is translated down by the same amount the
 * body shrank. Total visible height therefore becomes {@code TOTAL_HEADS} original head heights.
 * Measuring uses the core head cube only: hat, ears and the villager nose are decorations.
 */
public final class PenguinProportions {
    /** Visible total height in original head heights. Change this one value for another ratio. */
    public static final float TOTAL_HEADS = 2.5F;
    /** Neck-to-foot model height of both supported vanilla model families. */
    public static final float REFERENCE_BODY_HEIGHT = 24.0F;
    /** Model-space Y of the foot plane; scaling must keep this plane fixed. */
    public static final float FOOT_Y = 24.0F;
    /** Core head cube height of HumanoidModel and its player/skeleton/zombie/piglin subclasses. */
    public static final float HUMANOID_HEAD_HEIGHT = 8.0F;
    /** Core head cube height of VillagerModel. */
    public static final float VILLAGER_HEAD_HEIGHT = 10.0F;

    private PenguinProportions() {
    }

    /**
     * Returns the profile for a supported original model, or {@code null} when the model family
     * has no proportions contract. Callers keep their existing model-family checks.
     */
    public static Profile forModel(EntityModel<?> model) {
        if (model instanceof VillagerModel<?>) {
            return Profile.of(VILLAGER_HEAD_HEIGHT);
        }
        if (model instanceof HumanoidModel<?>) {
            return Profile.of(HUMANOID_HEAD_HEIGHT);
        }
        return null;
    }

    /**
     * Derived values in model pixels. {@code bodyHeight} is the headless neck-to-foot span after
     * scaling, {@code bodyScale} is its ratio to the original 24-pixel span, and {@code headDrop}
     * is how far the unchanged head moves down to sit on the scaled body.
     */
    public record Profile(float headHeight, float bodyHeight, float bodyScale, float headDrop) {
        private static Profile of(float headHeight) {
            float bodyHeight = (TOTAL_HEADS - 1.0F) * headHeight;
            float bodyScale = bodyHeight / REFERENCE_BODY_HEIGHT;
            float headDrop = FOOT_Y - bodyHeight;
            return new Profile(headHeight, bodyHeight, bodyScale, headDrop);
        }

        /** The head translation in PoseStack blocks (16 model pixels per block). */
        public float headDropBlocks() {
            return this.headDrop / 16.0F;
        }
    }
}
