package com.luochun.kusopenguin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One evaluated player posture per render frame. The body adapters copy native rotations, but
 * player-model translations are authored in native pixels while the penguin body is drawn at
 * {@code profile.bodyScale()}. This helper converts those deltas back to blocks and derives the
 * single head-follow frame used by the head, hat/armor layers and the body layer.
 *
 * <p>Only existing native states are fitted: {@code isCrouching}, the model riding flag,
 * {@code Pose.SITTING} and {@code swimAmount}. No movement, collision or input is changed.
 */
final class PenguinPlayerPosture {
    enum Kind { NONE, CROUCHING, SITTING, SWIMMING }

    private static final float RIDING_LEG_X_ROT = -1.4137167F;
    private static final float RIDING_LEG_Y_ROT = (float) (Math.PI / 10.0);
    private static final float RIDING_LEG_Z_ROT = 0.07853982F;
    private static final PenguinPlayerPosture NONE =
            new PenguinPlayerPosture(Kind.NONE, null);

    private final Kind kind;
    private final Matrix4f headDelta;
    private final float bodyLift, rightFootLift, leftFootLift;

    private PenguinPlayerPosture(Kind kind, Matrix4f headDelta) {
        this(kind,headDelta,0,0,0);
    }

    private PenguinPlayerPosture(Kind kind, Matrix4f headDelta, float bodyLift, float rightFootLift, float leftFootLift) {
        this.kind=kind;this.headDelta=headDelta;
        this.bodyLift=bodyLift;this.rightFootLift=rightFootLift;this.leftFootLift=leftFootLift;
    }

    static PenguinPlayerPosture of(LivingEntity entity, EntityModel<?> model,
                                   PenguinProportions.Profile profile, PenguinBodyModel penguin) {
        if (!(entity instanceof Player) || !(model instanceof HumanoidModel<?> humanoid)
                || profile == null || penguin == null || PenguinDeathClient.rendering(entity)) {
            return NONE;
        }
        Kind kind = classify(entity, humanoid);
        if (kind == Kind.NONE) {
            return NONE;
        }
        Matrix4f head=headDelta(humanoid,profile,penguin);
        PenguinPlayerPosture posture=new PenguinPlayerPosture(kind,head);
        PenguinMeshModel mesh=PenguinMeshModel.current();
        if(kind==Kind.CROUCHING && mesh!=null) {
            // The external PlayerRenderer crouch offset is -2/16 world blocks; its model scale
            // is .9375. Correct that once in model-root space, together with actual torso/feet bounds.
            penguin.resetPose();PenguinPoseAdapters.find(model).apply(penguin,model,entity);
            posture.applyCorrections(penguin,model);
            float ground=profile.footY()/16F-(2F/16F)/.9375F;
            float torso=lift(mesh,penguin.body(),"body",profile,ground);
            float right=lift(mesh,penguin.rightFoot(),"right_foot",profile,ground);
            float left=lift(mesh,penguin.leftFoot(),"left_foot",profile,ground);
            head=new Matrix4f().translation(0,-torso*profile.bodyScale()/16F,0).mul(head);
            posture=new PenguinPlayerPosture(kind,head,torso,right,left);
        }
        return posture;
    }

    private static float lift(PenguinMeshModel mesh,ModelPart part,String name,
                              PenguinProportions.Profile profile,float ground) {
        float max=profile.bodyOffsetBlocks()+mesh.maxY(part,name)*profile.bodyScale();
        return Math.max(0,max-ground)*16F/profile.bodyScale();
    }

    private static Kind classify(LivingEntity entity, HumanoidModel<?> model) {
        if (model.riding) {
            return Kind.SITTING;
        }
        if (entity.hasPose(Pose.SITTING)) {
            return Kind.SITTING;
        }
        if (model.swimAmount > 0.0F) {
            return Kind.SWIMMING;
        }
        if (entity.isCrouching()) {
            return Kind.CROUCHING;
        }
        return Kind.NONE;
    }

    boolean suppressesSpecialClips() {
        return this.kind != Kind.NONE;
    }

    void applyHead(com.mojang.blaze3d.vertex.PoseStack poseStack) {
        if (this.headDelta != null) {
            poseStack.mulPose(this.headDelta);
        }
    }

    /**
     * Rewrites the adapter's native-pixel translation deltas so their block displacement survives
     * the penguin body scale. Rotations copied by the adapter are left untouched.
     */
    void applyCorrections(PenguinBodyModel penguin, EntityModel<?> original) {
        if (this.kind == Kind.NONE || !(original instanceof HumanoidModel<?> humanoid)) {
            return;
        }
        PenguinProportions.Profile profile = PenguinProportions.forModel(original);
        if (profile == null) {
            return;
        }
        scaleTranslation(humanoid.body, penguin.body(), profile);
        // The adapter maps humanoid anatomical arms to the opposite historical wing names.
        scaleTranslation(humanoid.rightArm, penguin.leftWing(), profile);
        scaleTranslation(humanoid.leftArm, penguin.rightWing(), profile);
        scaleTranslation(humanoid.rightLeg, penguin.rightFoot(), profile);
        scaleTranslation(humanoid.leftLeg, penguin.leftFoot(), profile);
        penguin.body().y-=bodyLift;
        penguin.rightWing().y-=bodyLift;penguin.leftWing().y-=bodyLift;
        penguin.rightFoot().y-=rightFootLift;penguin.leftFoot().y-=leftFootLift;
        if (this.kind == Kind.SITTING && !humanoid.riding) {
            foldShortFeet(penguin);
        }
    }

    private static void scaleTranslation(ModelPart source, ModelPart target,
                                         PenguinProportions.Profile profile) {
        PartPose sourceInitial = source.getInitialPose();
        PartPose targetInitial = target.getInitialPose();
        float scale = profile.bodyScale();
        target.x = targetInitial.x + (source.x - sourceInitial.x) / scale;
        target.y = targetInitial.y + (source.y - sourceInitial.y) / scale;
        target.z = targetInitial.z + (source.z - sourceInitial.z) / scale;
    }

    private static void foldShortFeet(PenguinBodyModel penguin) {
        ModelPart right = penguin.rightFoot();
        right.xRot = RIDING_LEG_X_ROT;
        right.yRot = RIDING_LEG_Y_ROT;
        right.zRot = RIDING_LEG_Z_ROT;
        ModelPart left = penguin.leftFoot();
        left.xRot = RIDING_LEG_X_ROT;
        left.yRot = -RIDING_LEG_Y_ROT;
        left.zRot = -RIDING_LEG_Z_ROT;
    }

    /**
     * Standing neck anchor to posture neck anchor, measured in renderer root block units. The
     * head keeps its native size and current native rotation; only the mount translation follows
     * the scaled penguin chest.
     */
    private static Matrix4f headDelta(HumanoidModel<?> humanoid,
                                      PenguinProportions.Profile profile,
                                      PenguinBodyModel penguin) {
        PartPose penguinInitial = penguin.body().getInitialPose();
        PartPose bodyInitial = humanoid.body.getInitialPose();
        float scale = profile.bodyScale();
        float x = penguinInitial.x + (humanoid.body.x - bodyInitial.x) / scale;
        float y = penguinInitial.y + (humanoid.body.y - bodyInitial.y) / scale;
        float z = penguinInitial.z + (humanoid.body.z - bodyInitial.z) / scale;

        Matrix4f standing = bodyFrame(penguinInitial.x, penguinInitial.y, penguinInitial.z,
                bodyInitial.xRot, bodyInitial.yRot, bodyInitial.zRot, profile);
        Matrix4f current = bodyFrame(x, y, z, humanoid.body.xRot, humanoid.body.yRot,
                humanoid.body.zRot, profile);
        Matrix4f bodyChange = current.mul(standing.invert());

        PartPose headInitial = humanoid.head.getInitialPose();
        Vector3f standingHead = new Vector3f(headInitial.x, headInitial.y, headInitial.z)
                .div(16.0F);
        // headMatrix includes the same headDrop translation used by PenguinHeadRenderer.
        standingHead.y += profile.headDropBlocks();
        Vector3f target = bodyChange.transformPosition(standingHead);

        Matrix4f currentHead = PenguinHeadRenderer.headMatrix(humanoid);
        Quaternionf headRotation = currentHead.getUnnormalizedRotation(new Quaternionf())
                .normalize();
        return new Matrix4f().translation(target).rotate(headRotation)
                .mul(new Matrix4f(currentHead).invert());
    }

    private static Matrix4f bodyFrame(float x, float y, float z, float xRot, float yRot,
                                      float zRot, PenguinProportions.Profile profile) {
        return new Matrix4f()
                .translation(0.0F, profile.bodyOffsetBlocks(), 0.0F)
                .scale(profile.bodyScale() / 16.0F)
                .translate(x, y, z)
                .rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
    }
}
