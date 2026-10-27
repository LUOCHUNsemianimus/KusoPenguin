package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.client.compat.GeoMaidView;
import com.luochun.kusopenguin.client.compat.MaidCompatClient;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;

/**
 * Explicit animation adapters. ModelPart names alone are not treated as a compatibility contract:
 * humanoid parts are mapped one by one, while the villager's combined crossed "arms" part is
 * split onto the two wings.
 */
public final class PenguinPoseAdapters {
    private static final float VILLAGER_WING_CROSS_Z = 0.9F;

    private PenguinPoseAdapters() {
    }

    public static PenguinPoseAdapter find(EntityModel<?> original) {
        // Optional Gecko maid proxy supplies the five standard roots from the live Gecko bones.
        if (original instanceof GeoMaidView view) {
            return view.kusoPenguin$adapter();
        }
        if (original instanceof WardenModel<?>) return PenguinWardenPose::apply;
        if (original instanceof CreeperModel<?>) return PenguinPoseAdapters::applyCreeper;
        if (original instanceof IronGolemModel<?>) return PenguinPoseAdapters::applyGolem;
        if (original instanceof VillagerModel<?>) {
            return PenguinPoseAdapters::applyVillager;
        }
        if (original instanceof HumanoidModel<?>) {
            return PenguinPoseAdapters::applyHumanoid;
        }
        // Optional maid Bedrock models: guarded facade returns null when the API is absent or
        // a required standard bone/head is missing, keeping the complete native renderer.
        PenguinPoseAdapter maid = MaidCompatClient.adapter(original);
        if (maid != null) {
            return maid;
        }
        return null;
    }

    private static void applyCreeper(PenguinBodyModel penguin, EntityModel<?> original,
                                     LivingEntity entity) {
        ModelPart root = ((CreeperModel<?>) original).root();
        copyPart(root.getChild("body"), penguin.body());
        // The vanilla model pairs diagonal legs. Use its two hind-leg phases for our two feet.
        copyPart(root.getChild("left_hind_leg"), penguin.rightFoot());
        copyPart(root.getChild("right_hind_leg"), penguin.leftFoot());
        // A creeper has no animated arms; retain the penguin wings' resting bind pose.
    }

    private static void applyGolem(PenguinBodyModel penguin, EntityModel<?> original,
                                   LivingEntity entity) {
        ModelPart root = ((IronGolemModel<?>) original).root();
        copyPart(root.getChild("body"), penguin.body());
        copyPart(root.getChild("right_arm"), penguin.rightWing());
        copyPart(root.getChild("left_arm"), penguin.leftWing());
        copyPart(root.getChild("right_leg"), penguin.rightFoot());
        copyPart(root.getChild("left_leg"), penguin.leftFoot());
    }

    private static void applyHumanoid(PenguinBodyModel penguin, EntityModel<?> original,
                                      LivingEntity entity) {
        HumanoidModel<?> humanoid = (HumanoidModel<?>) original;
        copyPart(humanoid.body, penguin.body());
        // Historical wing names use the opposite X side from vanilla anatomical arms.
        copyPart(humanoid.rightArm, penguin.leftWing());
        copyPart(humanoid.leftArm, penguin.rightWing());
        copyPart(humanoid.rightLeg, penguin.rightFoot());
        copyPart(humanoid.leftLeg, penguin.leftFoot());
    }

    private static void applyVillager(PenguinBodyModel penguin, EntityModel<?> original,
                                      LivingEntity entity) {
        VillagerModel<?> villager = (VillagerModel<?>) original;
        ModelPart root = villager.root();
        copyPart(root.getChild("body"), penguin.body());
        copyPart(root.getChild("right_leg"), penguin.rightFoot());
        copyPart(root.getChild("left_leg"), penguin.leftFoot());

        // VillagerModel has one static, combined "arms" part (crossed in front of the chest).
        // The penguin has two hanging wings, so use the arm part's rotation and add opposite
        // Z rotations to reproduce the crossed shape with the two separate wings.
        ModelPart arms = root.getChild("arms");
        penguin.rightWing().xRot = arms.xRot;
        penguin.rightWing().yRot = arms.yRot;
        penguin.rightWing().zRot = arms.zRot - VILLAGER_WING_CROSS_Z;
        penguin.leftWing().xRot = arms.xRot;
        penguin.leftWing().yRot = arms.yRot;
        penguin.leftWing().zRot = arms.zRot + VILLAGER_WING_CROSS_Z;
    }

    /**
     * Copy the source rotation and its translation delta from the source's initial pose. Copying
     * the whole pose would replace the penguin's own pivots and dimensions with human lengths.
     */
    private static void copyPart(ModelPart source, ModelPart target) {
        PartPose initial = source.getInitialPose();
        target.x += source.x - initial.x;
        target.y += source.y - initial.y;
        target.z += source.z - initial.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
    }
}
