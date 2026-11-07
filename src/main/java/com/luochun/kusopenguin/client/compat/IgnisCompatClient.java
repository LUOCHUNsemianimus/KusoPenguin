package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.BossDancePose;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinMeshModel;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRig;
import com.luochun.kusopenguin.client.PenguinSkinnedMesh;
import com.luochun.kusopenguin.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Optional Ignis3.33 visual pilot: fat body, dangling feet, native jet and long armored arms. */
public final class IgnisCompatClient {
    private static final String MODEL="com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model";
    private static final String[] ROOT={"root"};
    private static final String[] BODY={"root","upperbody"};
    private static final String[] HEAD={"root","upperbody","head"};
    // Penguin historical positive-X wing follows anatomical native left arm.
    private static final String[] LEFT_JOINT={"root","upperbody","left_arm_joint"};
    private static final String[] RIGHT_JOINT={"root","upperbody","right_arm_joint"};
    private static final String[] LEFT_ARM={"root","upperbody","left_arm_joint","left_arm"};
    private static final String[] RIGHT_ARM={"root","upperbody","right_arm_joint","right_arm"};
    private static final String[] SHIELD={"root","upperbody","left_arm_joint","left_arm",
            "left_hand","left_big_guantlet","shield_root"};
    private static final String[] FLAME={"root","lowerbody","lowerbody2"};

    private IgnisCompatClient() {}

    public static boolean renderNativePass(LivingEntity entity,EntityModel<?> model,
            PoseStack stack,VertexConsumer nativeBuffer,int light,int overlay,int color,
            MultiBufferSource buffers) {
        Profile profile=replacementProfile(entity,model);
        if(profile==null) return false;
        PenguinAnimationClip bind=PenguinMeshModel.animationClip();
        PenguinSkinnedMesh skin=PenguinMeshModel.ignisSkin();
        Matrix4f[] nativeWorld=profile.world(bind);
        BossDancePose.Sample dance=BossDancePose.sample(entity,
                nativeWorld[PenguinRig.PELVIS],bind);
        Matrix4f[] world=dance==null?nativeWorld:profile.dancedWorld(dance,bind);
        // Submit retained native geometry before asking for a new material (BufferSource flush).
        if(dance==null) {
            profile.main.render(stack,nativeBuffer,light,overlay,color);
        } else {
            // Native head and flame stay untouched; only each anatomical arm subtree receives the
            // same shoulder delta that its cuff skin uses. Order matches the retained list.
            profile.head.render(stack,nativeBuffer,light,overlay,color);
            renderArm(stack,nativeBuffer,light,overlay,color,profile.leftArm,
                    profile.leftDelta(dance));
            renderArm(stack,nativeBuffer,light,overlay,color,profile.rightArm,
                    profile.rightDelta(dance));
            profile.flame.render(stack,nativeBuffer,light,overlay,color);
        }
        VertexConsumer penguin=buffers.getBuffer(RenderType.entityCutoutNoCull(PenguinMeshModel.TEXTURE));
        skin.render(stack,penguin,light,overlay,color,
                PenguinRenderContext.fromNativeRig(world,new Matrix4f(),bind));
        return true;
    }

    /** Own layer-model instance, original shield/crack material and visibility policy. */
    public static boolean renderShieldPass(LivingEntity entity,EntityModel<?> model,
            PoseStack stack,VertexConsumer buffer,int light,int overlay,int color) {
        Profile profile=replacementProfile(entity,model);
        if(profile==null) return false;
        PenguinAnimationClip bind=PenguinMeshModel.animationClip();
        BossDancePose.Sample dance=BossDancePose.sample(entity,profile.ordinaryPelvis(bind),bind);
        if(dance==null) {
            profile.shield.render(stack,buffer,light,overlay,color);
        } else {
            renderArm(stack,buffer,light,overlay,color,profile.shield,
                    profile.leftDelta(dance));
        }
        return true;
    }

    /** Exclude removed torso crack geometry; keep original crack pixels on retained attachments. */
    public static boolean renderArmorPass(LivingEntity entity,EntityModel<?> model,
            PoseStack stack,VertexConsumer buffer,int light,int overlay,int color) {
        Profile profile=replacementProfile(entity,model);
        if(profile==null) return false;
        PenguinAnimationClip bind=PenguinMeshModel.animationClip();
        BossDancePose.Sample dance=BossDancePose.sample(entity,profile.ordinaryPelvis(bind),bind);
        if(dance==null) {
            profile.armor.render(stack,buffer,light,overlay,color);
        } else {
            profile.head.render(stack,buffer,light,overlay,color);
            renderArm(stack,buffer,light,overlay,color,profile.leftArm,
                    profile.leftDelta(dance));
            renderArm(stack,buffer,light,overlay,color,profile.rightArm,
                    profile.rightDelta(dance));
        }
        return true;
    }

    /** Scoped native-arm draw: the pose-stack rotation is always popped, never cached. */
    private static void renderArm(PoseStack stack,VertexConsumer buffer,int light,int overlay,
            int color,NativeAdvancedRetainedGeometry geometry,Matrix4f pixelDelta) {
        stack.pushPose();
        try {
            stack.mulPose(BossDancePose.toBlocks(pixelDelta));
            geometry.render(stack,buffer,light,overlay,color);
        } finally {
            stack.popPose();
        }
    }

    private static Profile replacementProfile(LivingEntity entity,EntityModel<?> model) {
        if(!ModList.get().isLoaded("cataclysm") || !ModList.get().isLoaded("lionfishapi")
                || model==null || !MODEL.equals(model.getClass().getName())
                || !PenguinEntitySupport.isIgnis(entity.getType())
                || !entity.hasEffect(ModEffects.PENGUIN) || entity.isBaby()
                || entity.isInvisible() || entity.isCurrentlyGlowing() || entity.isSpectator()
                || !PenguinMeshModel.ready() || PenguinMeshModel.ignisSkin()==null
                || PenguinMeshModel.animationClip()==null) return null;
        return resolve(model);
    }

    private static Profile resolve(EntityModel<?> model) {
        NativeAdvancedBoxRig.Path root=NativeAdvancedBoxRig.path(model,ROOT);
        NativeAdvancedBoxRig.Path body=NativeAdvancedBoxRig.path(model,BODY);
        NativeAdvancedBoxRig.Path head=NativeAdvancedBoxRig.path(model,HEAD);
        NativeAdvancedBoxRig.Path left=NativeAdvancedBoxRig.path(model,LEFT_ARM);
        NativeAdvancedBoxRig.Path right=NativeAdvancedBoxRig.path(model,RIGHT_ARM);
        NativeAdvancedBoxRig.Path lj=NativeAdvancedBoxRig.path(model,LEFT_JOINT);
        NativeAdvancedBoxRig.Path rj=NativeAdvancedBoxRig.path(model,RIGHT_JOINT);
        if(root==null||body==null||head==null||left==null||right==null||lj==null||rj==null) return null;
        if(!root.rest(0,19,0,0,0,0) || !body.rest(0,-24.5F,1,.3491F,0,0)
                || !head.rest(0,-14,2,-.3491F,0,0)
                || !lj.rest(15.8F,-14,3,-.3491F,0,0)
                || !rj.rest(-15.8F,-14,3,-.3491F,0,0)
                || !left.rest(0,0,0,0,0,-.2618F)
                || !right.rest(0,0,0,0,0,.2618F)) return null;
        // Suppressed/near-zero native body or sleeve chains keep the original whole-model draw.
        for(NativeAdvancedBoxRig.Path p:new NativeAdvancedBoxRig.Path[]{body,head,left,right}) {
            if(!p.visible() || !p.usableScale()) return null;
        }
        NativeAdvancedRetainedGeometry main=NativeAdvancedRetainedGeometry.resolve(model,
                HEAD,LEFT_ARM,RIGHT_ARM,FLAME);
        NativeAdvancedRetainedGeometry armor=NativeAdvancedRetainedGeometry.resolve(model,
                HEAD,LEFT_ARM,RIGHT_ARM);
        NativeAdvancedRetainedGeometry shield=NativeAdvancedRetainedGeometry.resolve(model,SHIELD);
        // Dance needs the same retained pieces as separately transformable arm subtrees.
        NativeAdvancedRetainedGeometry danceHead=NativeAdvancedRetainedGeometry.resolve(model,HEAD);
        NativeAdvancedRetainedGeometry danceLeft=NativeAdvancedRetainedGeometry.resolve(model,LEFT_ARM);
        NativeAdvancedRetainedGeometry danceRight=NativeAdvancedRetainedGeometry.resolve(model,RIGHT_ARM);
        NativeAdvancedRetainedGeometry danceFlame=NativeAdvancedRetainedGeometry.resolve(model,FLAME);
        if(main==null||shield==null||armor==null||danceHead==null||danceLeft==null
                ||danceRight==null||danceFlame==null) return null;
        Vector3f neck=head.initial().transformPosition(new Vector3f());
        // Float above the ground: original fat body uniformly scaled, with room for downward-pointing toes.
        // No grounding or native leg sampling. The unchanged lowerbody2 supplies animated fire.
        Matrix4f alignment=new Matrix4f().translation(0,neck.y,neck.z)
                .scale(1.55F);
        return new Profile(body,left,right,lj,rj,alignment,main,shield,armor,
                danceHead,danceLeft,danceRight,danceFlame);
    }

    private record Profile(NativeAdvancedBoxRig.Path body,NativeAdvancedBoxRig.Path left,
                           NativeAdvancedBoxRig.Path right,NativeAdvancedBoxRig.Path leftJoint,
                           NativeAdvancedBoxRig.Path rightJoint,Matrix4f alignment,
                           NativeAdvancedRetainedGeometry main,NativeAdvancedRetainedGeometry shield,
                           NativeAdvancedRetainedGeometry armor,NativeAdvancedRetainedGeometry head,
                           NativeAdvancedRetainedGeometry leftArm,
                           NativeAdvancedRetainedGeometry rightArm,
                           NativeAdvancedRetainedGeometry flame) {
        Matrix4f bodySkin() {
            return body.current().mul(body.initial().invert()).mul(new Matrix4f(alignment));
        }

        Matrix4f ordinaryPelvis(PenguinAnimationClip bind) {
            return new Matrix4f(bodySkin()).translate(bind.bind(PenguinRig.PELVIS));
        }

        Matrix4f[] world(PenguinAnimationClip bind) {
            Matrix4f bodySkin=bodySkin();
            Matrix4f[] world=new Matrix4f[PenguinRig.COUNT];
            for(int i=0;i<world.length;i++) world[i]=new Matrix4f(bodySkin).translate(bind.bind(i));
            // Fixed ankle pitch, not an animation channel. Hip/knee and welded belly vertices
            // follow the body, so feet remain connected while their toes dangle downward.
            for(int i:new int[]{12,15}) {
                Vector3f ankle=bind.bind(i);
                world[i]=new Matrix4f(bodySkin).translate(ankle)
                        .rotateX((float)Math.toRadians(60)).translate(-ankle.x,-ankle.y,-ankle.z)
                        .translate(bind.bind(i));
            }
            // Rigid cuffs are authored relative to their fixed wing pivots. Absolute arm frames
            // cancel those pivots through inverseBind, following native pre-bend with no stretch.
            // A2px inward cuff overlap closes the gap to the unchanged narrow penguin shoulders.
            // Only channels 4/7 carry cuff geometry; 5/8 keep bodySkin*T(bindJoint) so the idle
            // belly flippers stay exactly on the body.
            Matrix4f plus=left.current().translate(-2.0F,0,0).scale(1.8F,1.15F,1.15F);
            Matrix4f minus=right.current().translate(2.0F,0,0).scale(1.8F,1.15F,1.15F);
            world[4]=new Matrix4f(plus);
            world[7]=new Matrix4f(minus);
            return world;
        }

        /** Dance body placement, with cuffs kept on their ordinary absolute native sockets. */
        Matrix4f[] dancedWorld(BossDancePose.Sample dance,PenguinAnimationClip bind) {
            Matrix4f[] world=dance.placed();
            Matrix4f leftDelta=leftDelta(dance);
            Matrix4f rightDelta=rightDelta(dance);
            // The raw sampled cuff matrices are deliberately not used: the existing socket,
            // overlap and scale stay, rotated by the dance wing delta around the same shoulder.
            // Only channels 4/7 own the cuffs.
            Matrix4f plus=left.current().translate(-2.0F,0,0).scale(1.8F,1.15F,1.15F);
            Matrix4f minus=right.current().translate(2.0F,0,0).scale(1.8F,1.15F,1.15F);
            world[4]=new Matrix4f(leftDelta).mul(plus);
            world[7]=new Matrix4f(rightDelta).mul(minus);
            // Belly flippers use the sampled pelvis as body skin base, then rotate around their
            // actual upper roots by the same chest-relative wing quaternion as the native arms.
            Matrix4f bodySkin=new Matrix4f(world[PenguinRig.PELVIS])
                    .translate(bind.bind(PenguinRig.PELVIS).negate());
            world[5]=bellyWorld(bodySkin,dance,PenguinRig.RIGHT_WING,
                    new Vector3f(5.45F,2.6F,.2F),bind.bind(5));
            world[8]=bellyWorld(bodySkin,dance,PenguinRig.LEFT_WING,
                    new Vector3f(-5.45F,2.6F,.2F),bind.bind(8));
            return world;
        }

        /** Rigid body-shoulder delta for one belly flipper channel. */
        private static Matrix4f bellyWorld(Matrix4f bodySkin,BossDancePose.Sample dance,int wing,
                Vector3f pivot,Vector3f bindJoint) {
            return new Matrix4f(bodySkin)
                    .translate(pivot)
                    .rotate(dance.wingRotation(wing))
                    .translate(-pivot.x,-pivot.y,-pivot.z)
                    .translate(bindJoint);
        }

        /** Positive-X penguin wing maps to the native anatomical left arm. */
        Matrix4f leftDelta(BossDancePose.Sample dance) {
            return dance.shoulderDelta(PenguinRig.RIGHT_WING,leftJoint.current());
        }

        Matrix4f rightDelta(BossDancePose.Sample dance) {
            return dance.shoulderDelta(PenguinRig.LEFT_WING,rightJoint.current());
        }
    }
}
