package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.animation.PenguinAction;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;

/** Per-draw evaluated matrices. All input bind/clip data remain immutable. */
public final class PenguinAnimationPose {
    public final float weight;
    final Matrix4f[] world=new Matrix4f[PenguinRig.COUNT];
    final Matrix4f[] skin=new Matrix4f[PenguinRig.COUNT];
    final Matrix4f headDelta;
    private PenguinAnimationPose(PenguinAction action,PenguinAnimationClip clip,PenguinAnimationClip.Sample sample,
                                 PenguinBodyModel nativeParts,EntityModel<?> original) {
        weight=sample.weight();
        world[0]=local(sample,0);
        Matrix4f nativeBody=part(nativeParts.body());
        world[1]=new Matrix4f(world[0]).mul(nativeBody).translate(clip.bind(1)).mul(local(sample,1));
        child(clip,sample,2);child(clip,sample,3);
        wing(clip,sample,4,nativeParts.rightWing());wing(clip,sample,7,nativeParts.leftWing());
        leg(clip,sample,10,nativeParts.rightFoot(),action);leg(clip,sample,13,nativeParts.leftFoot(),action);
        for(int i=0;i<skin.length;i++)skin[i]=new Matrix4f(world[i]).translate(clip.bind(i).negate());
        PenguinProportions.Profile profile=PenguinProportions.forModel(original);
        Matrix4f nativeHead=PenguinHeadRenderer.headMatrix(original);
        Quaternionf nativeBodyQ=rotation(nativeBody),nativeHeadQ=rotation(nativeHead),chestQ=rotation(world[2]);
        Quaternionf localNativeHead=new Quaternionf(nativeBodyQ).conjugate().mul(nativeHeadQ);
        Quaternionf headQ=new Quaternionf(chestQ).mul(localNativeHead.slerp(sample.rotations()[3],weight)).normalize();
        Vector3f expectedNativeNeck=nativeBody.getTranslation(new Vector3f()).mul(profile.bodyScale()/16)
                .add(0,profile.bodyOffsetBlocks(),0);
        Vector3f pivotOffset=nativeHead.getTranslation(new Vector3f()).sub(expectedNativeNeck);
        new Quaternionf(chestQ).mul(new Quaternionf(nativeBodyQ).conjugate()).transform(pivotOffset);
        Vector3f target=world[3].getTranslation(new Vector3f()).mul(profile.bodyScale()/16)
                .add(0,profile.bodyOffsetBlocks(),0).add(pivotOffset);
        // Only the mount position inherits stretch. Head geometry gets a rigid rotation.
        headDelta=new Matrix4f().translation(target).rotate(headQ).mul(new Matrix4f(nativeHead).invert());
    }
    public static PenguinAnimationPose evaluate(PenguinAnimationClip clip,PenguinAnimationClip.Sample sample,
                                                PenguinBodyModel parts,EntityModel<?> original) {
        return evaluate(PenguinAction.TAUNT,clip,sample,parts,original);
    }
    public static PenguinAnimationPose evaluate(PenguinAction action,PenguinAnimationClip clip,
                                                PenguinAnimationClip.Sample sample,PenguinBodyModel parts,
                                                EntityModel<?> original) {
        return new PenguinAnimationPose(action,clip,sample,parts,original);
    }
    private void child(PenguinAnimationClip clip,PenguinAnimationClip.Sample s,int i){
        int p=PenguinRig.PARENTS[i];Vector3f delta=clip.bind(i).sub(clip.bind(p));
        world[i]=new Matrix4f(world[p]).translate(delta).mul(local(s,i));
    }
    private void wing(PenguinAnimationClip clip,PenguinAnimationClip.Sample s,int i,ModelPart nativeWing){
        Matrix4f base=part(nativeWing);Vector3f nativePos=base.getTranslation(new Vector3f());world[0].transformPosition(nativePos);
        Vector3f target=clip.bind(i).sub(clip.bind(2));world[2].transformPosition(target);
        Vector3f extra=new Vector3f(s.translations()[i]).mul(weight);rotation(world[2]).transform(extra);target.add(extra);
        Quaternionf nativeQ=rotation(world[0]).mul(rotation(base));
        Quaternionf goal=rotation(world[2]).mul(s.rotations()[i]);
        world[i]=new Matrix4f().translation(nativePos.lerp(target,weight))
                .rotate(nativeQ.slerp(goal,weight).normalize());
        child(clip,s,i+1);child(clip,s,i+2);
    }
    private void leg(PenguinAnimationClip clip,PenguinAnimationClip.Sample s,int i,ModelPart nativeLeg,
                     PenguinAction action){
        if(action==PenguinAction.FLYING_KICK){
            // The kick leg hangs from the animated pelvis. Blend the native short leg into that
            // fixed hip bind target, then let the knee/foot bind chain carry the clip.
            Matrix4f nativeBase=new Matrix4f(world[0]).mul(part(nativeLeg));
            Vector3f nativePos=nativeBase.getTranslation(new Vector3f());
            Quaternionf nativeQ=rotation(nativeBase);
            Matrix4f target=new Matrix4f(world[PenguinRig.PELVIS])
                    .translate(new Vector3f(clip.bind(i)).sub(clip.bind(PenguinRig.PELVIS)))
                    .translate(s.translations()[i]).rotate(s.rotations()[i]);
            Vector3f targetPos=target.getTranslation(new Vector3f());
            Quaternionf targetQ=rotation(target);
            world[i]=new Matrix4f().translation(nativePos.lerp(targetPos,weight))
                    .rotate(nativeQ.slerp(targetQ,weight).normalize());
        }else{
            // Taunt keeps native short-leg motion and planting. Hip/knee/foot are separately drivable.
            world[i]=new Matrix4f(world[0]).mul(part(nativeLeg)).mul(local(s,i));
        }
        child(clip,s,i+1);child(clip,s,i+2);
    }
    private static Matrix4f local(PenguinAnimationClip.Sample s,int i){
        Vector3f scale=new Vector3f(1).lerp(s.scales()[i],s.weight());
        return new Matrix4f().translate(new Vector3f(s.translations()[i]).mul(s.weight()))
                .rotate(new Quaternionf().slerp(s.rotations()[i],s.weight()).normalize()).scale(scale);
    }
    static Matrix4f part(ModelPart p){
        return new Matrix4f().translation(p.x,p.y,p.z).rotateZYX(p.zRot,p.yRot,p.xRot)
                .scale(p.xScale,p.yScale,p.zScale);
    }
    private static Quaternionf rotation(Matrix4f m){return m.getUnnormalizedRotation(new Quaternionf()).normalize();}
}
