package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

/** Nestable render scope: all head/body/decorative passes share one evaluated entity pose. */
public final class PenguinRenderContext {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES=ThreadLocal.withInitial(ArrayDeque::new);
    private static PenguinBodyModel shared;
    private static final class Frame {
        final LivingEntity entity;final EntityModel<?> model;final float partial;
        boolean evaluated;PenguinAnimationPose pose;
        Frame(LivingEntity entity,EntityModel<?> model,float partial){this.entity=entity;this.model=model;this.partial=partial;}
    }
    private PenguinRenderContext(){}
    public static void setModel(PenguinBodyModel model){shared=model;}
    public static void push(LivingEntity entity,EntityModel<?> model,float partial){FRAMES.get().push(new Frame(entity,model,partial));}
    public static void pop(){ArrayDeque<Frame> frames=FRAMES.get();frames.pop();if(frames.isEmpty())FRAMES.remove();}
    public static PenguinAnimationPose pose(){
        Frame f=FRAMES.get().peek();if(f==null)return null;
        if(f.evaluated)return f.pose;f.evaluated=true;
        if(shared==null||!PenguinMeshPreview.isEnabled()||!PenguinRenderSupport.shouldReplace(f.entity))return null;
        PenguinAnimationClip clip=PenguinMeshModel.animationClip();if(clip==null)return null;
        float age=PenguinPoseClient.age(f.entity,f.partial);
        if(age<0||age>=clip.totalDuration())return null;
        PenguinAnimationClip.Sample sample=clip.sample(age);if(sample.weight()<=0)return null;
        PenguinPoseAdapter adapter=PenguinPoseAdapters.find(f.model);if(adapter==null)return null;
        shared.resetPose();adapter.apply(shared,f.model,f.entity);
        f.pose=PenguinAnimationPose.evaluate(clip,sample,shared,f.model);return f.pose;
    }
    public static void applyHead(PoseStack stack){PenguinAnimationPose p=pose();if(p!=null)stack.mulPose(p.headDelta);}
}
