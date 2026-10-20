package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.luochun.kusopenguin.animation.PenguinAction;
import java.util.ArrayDeque;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/** Nestable render scope: all head/body/decorative passes share one evaluated entity pose. */
public final class PenguinRenderContext {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES=ThreadLocal.withInitial(ArrayDeque::new);
    private static PenguinBodyModel shared;
    private static final class Frame {
        final LivingEntity entity;final EntityModel<?> model;final float partial;
        boolean evaluated;PenguinAnimationPose pose;
        boolean postureEvaluated;PenguinPlayerPosture posture;
        Frame(LivingEntity entity,EntityModel<?> model,float partial){this.entity=entity;this.model=model;this.partial=partial;}
    }
    private PenguinRenderContext(){}
    public static void setModel(PenguinBodyModel model){shared=model;}
    public static void push(LivingEntity entity,EntityModel<?> model,float partial){FRAMES.get().push(new Frame(entity,model,partial));}
    public static void pop(){ArrayDeque<Frame> frames=FRAMES.get();frames.pop();if(frames.isEmpty())FRAMES.remove();}
    static PenguinPlayerPosture posture(){
        Frame f=FRAMES.get().peek();if(f==null)return null;
        if(f.postureEvaluated)return f.posture;f.postureEvaluated=true;
        if(shared==null||!PenguinRenderSupport.shouldReplace(f.entity))return null;
        f.posture=PenguinPlayerPosture.of(f.entity,f.model,PenguinProportions.forModel(f.model),shared);
        return f.posture;
    }
    /**
     * Single reset/adapt/correct entry point for the shared body. The player posture applies after
     * the adapter so generic mob adapters and the animation rig keep their previous transforms.
     */
    static void prepareBody(PenguinBodyModel penguin){
        Frame f=FRAMES.get().peek();
        penguin.resetPose();
        if(f==null)return;
        PenguinPoseAdapter adapter=PenguinPoseAdapters.find(f.model);
        if(adapter==null)return;
        adapter.apply(penguin,f.model,f.entity);
        PenguinPlayerPosture posture=posture();
        if(posture!=null)posture.applyCorrections(penguin,f.model);
    }
    public static PenguinAnimationPose pose(){
        Frame f=FRAMES.get().peek();if(f==null)return null;
        if(f.evaluated)return f.pose;f.evaluated=true;
        if(shared==null||!PenguinMeshPreview.isEnabled()||!PenguinRenderSupport.shouldReplace(f.entity))return null;
        PenguinAction action=PenguinDeathClient.rendering(f.entity)?PenguinAction.DEATH:PenguinPoseClient.action(f.entity);if(action==null)return null;
        PenguinPlayerPosture posture=posture();
        // A non-standing posture uses the native player animation; ordinary taunt/kick clips must
        // not replace it. Death is a detached copy and keeps its dedicated animation.
        if(posture!=null&&posture.suppressesSpecialClips()&&action!=PenguinAction.DEATH)return null;
        PenguinAnimationClip clip=PenguinMeshModel.animationClip(action);if(clip==null)return null;
        float age=PenguinDeathClient.rendering(f.entity)?PenguinDeathClient.age(f.entity,f.partial):PenguinPoseClient.age(f.entity,action,f.partial);
        if(age<0||age>=clip.totalDuration())return null;
        PenguinAnimationClip.Sample sample=action==PenguinAction.DEATH?clip.sampleTerminal(age):clip.sample(age);if(sample.weight()<=0)return null;
        PenguinPoseAdapter adapter=PenguinPoseAdapters.find(f.model);if(adapter==null)return null;
        prepareBody(shared);
        f.pose=PenguinAnimationPose.evaluate(action,clip,sample,shared,f.model);return f.pose;
    }
    public static void applyHead(PoseStack stack){
        PenguinAnimationPose p=pose();
        if(p!=null){stack.mulPose(p.headDelta);return;}
        PenguinPlayerPosture posture=posture();
        if(posture!=null)posture.applyHead(stack);
    }
    /**
     * Replaces the vanilla humanoid hand frame with the current penguin wing end. Returns false
     * when this draw is not a supported penguin replacement so the vanilla hand keeps ownership.
     */
    public static boolean applyHand(PoseStack stack,HumanoidArm arm){
        Frame f=FRAMES.get().peek();
        if(f==null||shared==null||!PenguinRenderSupport.shouldReplace(f.entity))return false;
        PenguinProportions.Profile profile=PenguinProportions.forModel(f.model);
        PenguinPoseAdapter adapter=PenguinPoseAdapters.find(f.model);
        if(profile==null||adapter==null)return false;
        PenguinAnimationPose animated=pose();
        if(animated==null){
            // Outside the taunt clip (including BLOCK quality) the visible body is the adapter
            // pose, so reset the shared parts and reuse the exact wing ModelPart transform.
            prepareBody(shared);
        }
        PenguinHeldItemRenderer.apply(stack,profile,animated,shared,arm);
        return true;
    }
}
