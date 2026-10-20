package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Flattens Warden's animated bone/body hierarchy onto the five independent penguin roots. */
final class PenguinWardenPose {
    private PenguinWardenPose() {}

    static void apply(PenguinBodyModel penguin, EntityModel<?> original, LivingEntity entity) {
        WardenModel<?> warden = (WardenModel<?>) original;
        PenguinProportions.Profile profile = PenguinProportions.forModel(original);
        ModelPart root = warden.root();
        ModelPart bone = root.getChild("bone");
        ModelPart body = bone.getChild("body");
        PoseStack stack = new PoseStack();
        root.translateAndRotate(stack);
        bone.translateAndRotate(stack);
        Matrix4f boneWorld = new Matrix4f(stack.last().pose());
        body.translateAndRotate(stack);
        Matrix4f bodyWorld = new Matrix4f(stack.last().pose());

        // Native bind neck is body-local Y=-13. Head's own look/sniff animation stays head-only.
        Vector3f neck = bodyWorld.transformPosition(new Vector3f(0, -13.0F / 16, 0));
        assign(penguin.body(), neck, bodyWorld, profile);
        limb(penguin.rightWing(), body.getChild("right_arm"), bodyWorld, 3, profile);
        limb(penguin.leftWing(), body.getChild("left_arm"), bodyWorld, 3, profile);
        limb(penguin.rightFoot(), bone.getChild("right_leg"), boneWorld, 24, profile);
        limb(penguin.leftFoot(), bone.getChild("left_leg"), boneWorld, 24, profile);
    }

    private static void limb(ModelPart target, ModelPart source, Matrix4f parent,
                             float parentBindY, PenguinProportions.Profile profile) {
        PartPose bind = target.getInitialPose();
        PartPose sourceBind = source.getInitialPose();
        float s = profile.bodyScale();
        // Keep the custom shoulder/hip pivot; only transfer native translation DELTAS.
        Vector3f point = new Vector3f(
                (bind.x * s + source.x - sourceBind.x) / 16,
                (profile.bodyOffset() + bind.y * s - parentBindY + source.y - sourceBind.y) / 16,
                (bind.z * s + source.z - sourceBind.z) / 16);
        parent.transformPosition(point);
        Matrix4f rotation = new Matrix4f(parent)
                .rotateZYX(source.zRot, source.yRot, source.xRot)
                .scale(source.xScale, source.yScale, source.zScale);
        assign(target, point, rotation, profile);
    }

    private static void assign(ModelPart target, Vector3f position, Matrix4f rotation,
                               PenguinProportions.Profile profile) {
        float s = profile.bodyScale();
        target.setPos(position.x * 16 / s,
                (position.y * 16 - profile.bodyOffset()) / s, position.z * 16 / s);
        Quaternionf q = rotation.getUnnormalizedRotation(new Quaternionf()).normalize();
        // MC 1.21.1 ships JOML 1.10.5, whose getEulerAnglesZYX X denominator has a
        // +y*y term. Use the ZYX inverse explicitly so combined-axis animation stays exact.
        float sinY = Math.max(-1.0F, Math.min(1.0F, 2.0F * (q.w * q.y - q.x * q.z)));
        target.yRot = (float) Math.asin(sinY);
        if (Math.abs(sinY) < 0.999999F) {
            target.xRot = (float) Math.atan2(q.y * q.z + q.w * q.x,
                    0.5F - q.x * q.x - q.y * q.y);
            target.zRot = (float) Math.atan2(q.x * q.y + q.w * q.z,
                    0.5F - q.y * q.y - q.z * q.z);
        } else {
            // At the pole X/Z are not individually unique; X=0 gives the same orientation.
            target.xRot = 0.0F;
            target.zRot = (float) Math.atan2(2.0F * (q.w * q.z - q.x * q.y),
                    1.0F - 2.0F * (q.x * q.x + q.z * q.z));
        }
        Vector3f scale = rotation.getScale(new Vector3f());
        target.xScale = scale.x;
        target.yScale = scale.y;
        target.zScale = scale.z;
    }
}
