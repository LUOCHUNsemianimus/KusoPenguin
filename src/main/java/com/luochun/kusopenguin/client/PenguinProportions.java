package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.client.compat.GeoMaidView;
import com.luochun.kusopenguin.client.compat.MaidCompatClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.ZombieVillagerModel;

/** Head size stays native; neck and floor anchors are explicit for each model family. */
public final class PenguinProportions {
    public static final float TOTAL_HEADS = 2.5F;
    public static final float REFERENCE_BODY_HEIGHT = 24.0F;
    public static final float FOOT_Y = 24.0F;
    public static final float HUMANOID_HEAD_HEIGHT = 8.0F;
    public static final float VILLAGER_HEAD_HEIGHT = 10.0F;

    private PenguinProportions() {}

    public static Profile forModel(EntityModel<?> model) {
        // Optional Gecko maid proxy: profile is derived from the native bind head pivot, not from
        // animated bounds. A missing head returns null and keeps the complete native renderer.
        if (model instanceof GeoMaidView view) {
            return view.kusoPenguin$profile();
        }
        // These two inherit HumanoidModel but have different head/floor geometry.
        // Enderman legs end at Y=25, one pixel below the actual renderer ground plane.
        // Anchor our short feet at Y=24 so they remain visible above the terrain.
        if (model instanceof EndermanModel<?>) return chibi(8, -13, FOOT_Y);
        if (model instanceof ZombieVillagerModel<?>) return chibi(10, 0, FOOT_Y);
        if (model instanceof CreeperModel<?>) return chibi(8, 6, FOOT_Y);
        // Keep the native neck-to-floor span of these large mobs.
        if (model instanceof IronGolemModel<?>) return profile(10, -9, 24, 33);
        if (model instanceof WardenModel<?>) return profile(16, -10, 24, 34);
        if (model instanceof VillagerModel<?>) return chibi(10, 0, FOOT_Y);
        if (model instanceof HumanoidModel<?>) return chibi(8, 0, FOOT_Y);
        // Optional maid Bedrock models use the same explicit standard-bone contract as the other
        // upright families. The head stays native; the calibrated default head height is 8 pixels.
        Profile maid = MaidCompatClient.maidProfile(model);
        if (maid != null) return maid;
        return null;
    }

    /**
     * Explicit profile for the supported maid Bedrock contract: body, armLeft/armRight,
     * legLeft/legRight and head. {@code restNeckY} is the head pivot's stable rest position in
     * Java Bedrock pixels, derived from bind pivots and initial ancestor rotations. Head height
     * stays calibrated at 8 pixels, the shared body is 12 pixels and the physical floor stays 24.
     */
    public static Profile maid(float restNeckY) {
        return profile(8, restNeckY, FOOT_Y, 12);
    }

    /**
     * Fallback for a root-level standard head whose JSON pivot Y is 18, which converts to Java
     * Bedrock Y 6. Model-aware callers pass their derived rest neck to {@link #maid(float)}.
     */
    public static Profile maid() {
        return maid(6.0F);
    }

    /** Native head dimensions stay unchanged; only the dancing body becomes upright. */
    public static Profile dancingForModel(EntityModel<?> model) {
        Profile upright=forModel(model);
        if(upright!=null)return upright;
        float height;
        if(model instanceof net.minecraft.client.model.CowModel<?>)height=8;
        else if(model instanceof net.minecraft.client.model.SheepModel<?>||model instanceof net.minecraft.client.model.SheepFurModel<?>)height=6;
        else if(model instanceof net.minecraft.client.model.PigModel<?>)height=8;
        else if(model instanceof net.minecraft.client.model.CatModel<?>)height=4;
        else if(model instanceof net.minecraft.client.model.WolfModel<?>)height=6;
        else if(model instanceof net.minecraft.client.model.SpiderModel<?>)height=8;
        else return null;
        var head=PenguinHeadRenderer.headPart(model);
        return head==null?null:chibi(height,head.getInitialPose().y,FOOT_Y);
    }

    private static Profile chibi(float headHeight, float neckY, float footY) {
        return profile(headHeight, neckY, footY, (TOTAL_HEADS - 1.0F) * headHeight);
    }

    private static Profile profile(float headHeight, float neckY, float footY, float bodyHeight) {
        float offset = footY - bodyHeight;
        return new Profile(headHeight, bodyHeight, bodyHeight / REFERENCE_BODY_HEIGHT,
                offset - neckY, offset, footY);
    }

    /** Model-pixel coordinates, before the living renderer's own scale/rotation. */
    public record Profile(float headHeight, float bodyHeight, float bodyScale, float headDrop,
                          float bodyOffset, float footY) {
        public float headDropBlocks() { return headDrop / 16.0F; }
        public float bodyOffsetBlocks() { return bodyOffset / 16.0F; }
    }
}
