package com.luochun.kusopenguin.client.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
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
        Matrix4f[] world=profile.world(bind);
        // Submit retained native geometry before asking for a new material (BufferSource flush).
        profile.main.render(stack,nativeBuffer,light,overlay,color);
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
        profile.shield.render(stack,buffer,light,overlay,color);
        return true;
    }

    /** Exclude removed torso crack geometry; keep original crack pixels on retained attachments. */
    public static boolean renderArmorPass(LivingEntity entity,EntityModel<?> model,
            PoseStack stack,VertexConsumer buffer,int light,int overlay,int color) {
        Profile profile=replacementProfile(entity,model);
        if(profile==null) return false;
        profile.armor.render(stack,buffer,light,overlay,color);
        return true;
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
        if(main==null||shield==null||armor==null) return null;
        Vector3f neck=head.initial().transformPosition(new Vector3f());
        // Float above the ground: original fat body uniformly scaled, with room for downward-pointing toes.
        // No grounding or native leg sampling. The unchanged lowerbody2 supplies animated fire.
        Matrix4f alignment=new Matrix4f().translation(0,neck.y,neck.z)
                .scale(1.55F);
        return new Profile(body,left,right,alignment,main,shield,armor);
    }

    private record Profile(NativeAdvancedBoxRig.Path body,NativeAdvancedBoxRig.Path left,
                           NativeAdvancedBoxRig.Path right,Matrix4f alignment,
                           NativeAdvancedRetainedGeometry main,NativeAdvancedRetainedGeometry shield,
                           NativeAdvancedRetainedGeometry armor) {
        Matrix4f[] world(PenguinAnimationClip bind) {
            Matrix4f delta=body.current().mul(body.initial().invert());
            Matrix4f bodySkin=delta.mul(new Matrix4f(alignment));
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
            Matrix4f plus=left.current().translate(-2.0F,0,0).scale(1.8F,1.15F,1.15F);
            Matrix4f minus=right.current().translate(2.0F,0,0).scale(1.8F,1.15F,1.15F);
            for(int i=4;i<=6;i++) world[i]=new Matrix4f(plus);
            for(int i=7;i<=9;i++) world[i]=new Matrix4f(minus);
            return world;
        }
    }
}
