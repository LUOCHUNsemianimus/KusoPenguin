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
                                 PenguinBodyModel nativeParts,EntityModel<?> original,
                                 PenguinProportions.Profile profile) {
        weight=sample.weight();
        world[0]=local(sample,0);
        Matrix4f nativeBody=part(nativeParts.body());
        world[1]=new Matrix4f(world[0]).mul(nativeBody).translate(clip.bind(1)).mul(local(sample,1));
        child(clip,sample,2);child(clip,sample,3);
        wing(clip,sample,4,nativeParts.rightWing());wing(clip,sample,7,nativeParts.leftWing());
        leg(clip,sample,10,nativeParts.rightFoot(),action);leg(clip,sample,13,nativeParts.leftFoot(),action);
        // One anatomical leg rebind for every action pose, before any skin matrix is derived.
        PenguinLegPose.apply(world,clip);
        for(int i=0;i<skin.length;i++)skin[i]=new Matrix4f(world[i]).translate(clip.bind(i).negate());
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
        if(action==PenguinAction.DEATH){
            PenguinSkinnedMesh mesh=PenguinMeshModel.currentSkin();
            float bodyY=profile.bodyOffsetBlocks()+mesh.maxY(this)*profile.bodyScale()/16;
            float headY=PenguinHeadRenderer.maxY(original,headDelta);
            float lift=java.lang.Math.min(0,profile.footY()/16-java.lang.Math.max(bodyY,headY));
            // Native model Y points down. Lift the whole rig and head together; no geometry stretch.
            if(lift<0){
                float pixels=lift*16/profile.bodyScale();
                for(int i=0;i<world.length;i++){world[i].m31(world[i].m31()+pixels);skin[i].m31(skin[i].m31()+pixels);}
                headDelta.m31(headDelta.m31()+lift);
            }
        }
    }
    static PenguinAnimationPose fromDance(Matrix4f[] matrices,Matrix4f headDelta,PenguinAnimationClip bindClip){
        if(matrices==null||matrices.length!=PenguinRig.COUNT){
            throw new IllegalArgumentException("dance world matrices must have "+PenguinRig.COUNT
                    +" entries, got "+(matrices==null?"null":matrices.length));
        }
        if(headDelta==null)throw new IllegalArgumentException("dance headDelta must not be null");
        if(bindClip==null)throw new IllegalArgumentException("dance bindClip must not be null");
        for(int i=0;i<matrices.length;i++){
            if(matrices[i]==null)throw new IllegalArgumentException("dance world matrix "+i+" must not be null");
        }
        return new PenguinAnimationPose(1f,matrices,headDelta,bindClip);
    }
    /**
     * Pure 16-joint body-only pose for an ordinary (non-action) draw of a schema 2 mesh.
     *
     * <p>The five native roots already contain their bind pivots. The constructor then applies the
     * shared {@link PenguinLegPose} anatomical rebind once, so pelvis-pinned seam vertices stay on
     * the torso while hip/knee/foot vertices keep their explicit weights and follow the ordinary
     * native leg rotations through the short control pivots. The root stays identity, the static
     * root motion is preserved, and {@code headDelta} stays identity: this factory is deliberately
     * NOT fed to action selection, the head or held-item semantics.
     * Matrices are in penguin model pixels, matching the baked skin.
     */
    static PenguinAnimationPose nativeBodyOnly(PenguinBodyModel parts,PenguinAnimationClip bind){
        if(parts==null)throw new IllegalArgumentException("native body-only pose needs parts");
        if(bind==null)throw new IllegalArgumentException("native body-only pose needs a bind clip");
        Matrix4f[] matrices=new Matrix4f[PenguinRig.COUNT];
        matrices[PenguinRig.ROOT]=new Matrix4f();
        Matrix4f body=part(parts.body());
        matrices[PenguinRig.PELVIS]=new Matrix4f(body).translate(bind.bind(PenguinRig.PELVIS));
        matrices[PenguinRig.CHEST]=new Matrix4f(matrices[PenguinRig.PELVIS])
                .translate(bind.bind(PenguinRig.CHEST).sub(bind.bind(PenguinRig.PELVIS)));
        matrices[PenguinRig.HEAD]=new Matrix4f(matrices[PenguinRig.CHEST])
                .translate(bind.bind(PenguinRig.HEAD).sub(bind.bind(PenguinRig.CHEST)));
        limb(matrices,bind,parts.rightWing(),PenguinRig.RIGHT_WING);
        limb(matrices,bind,parts.leftWing(),PenguinRig.LEFT_WING);
        limb(matrices,bind,parts.rightFoot(),PenguinRig.RIGHT_HIP);
        limb(matrices,bind,parts.leftFoot(),PenguinRig.LEFT_HIP);
        return new PenguinAnimationPose(matrices,bind);
    }
    private static void limb(Matrix4f[] matrices,PenguinAnimationClip bind,ModelPart rootPart,int root){
        matrices[root]=part(rootPart);
        matrices[root+1]=new Matrix4f(matrices[root]).translate(bind.bind(root+1).sub(bind.bind(root)));
        matrices[root+2]=new Matrix4f(matrices[root+1]).translate(bind.bind(root+2).sub(bind.bind(root+1)));
    }
    /** Native body-only constructor: root identity, weight 1, rigid identity head delta. */
    private PenguinAnimationPose(Matrix4f[] matrices,PenguinAnimationClip bindClip){
        weight=1f;
        // Copy first: callers such as the dance solver reuse their world array and must never
        // receive an already-retargeted leg chain. The anatomical rebind applies exactly once.
        for(int i=0;i<world.length;i++)world[i]=new Matrix4f(matrices[i]);
        PenguinLegPose.apply(world,bindClip);
        for(int i=0;i<skin.length;i++)skin[i]=new Matrix4f(world[i]).translate(bindClip.bind(i).negate());
        headDelta=new Matrix4f();
    }
    private PenguinAnimationPose(float weight,Matrix4f[] matrices,Matrix4f headDelta,PenguinAnimationClip bindClip){
        this.weight=weight;
        // Same copy-once contract as the body-only factory: the caller's rig stays unchanged.
        for(int i=0;i<world.length;i++)world[i]=new Matrix4f(matrices[i]);
        PenguinLegPose.apply(world,bindClip);
        for(int i=0;i<skin.length;i++)skin[i]=new Matrix4f(world[i]).translate(bindClip.bind(i).negate());
        this.headDelta=new Matrix4f(headDelta);
    }
    public static PenguinAnimationPose evaluate(PenguinAnimationClip clip,PenguinAnimationClip.Sample sample,
                                                PenguinBodyModel parts,EntityModel<?> original) {
        return evaluate(PenguinAction.TAUNT,clip,sample,parts,original);
    }
    public static PenguinAnimationPose evaluate(PenguinAction action,PenguinAnimationClip clip,
                                                PenguinAnimationClip.Sample sample,PenguinBodyModel parts,
                                                EntityModel<?> original) {
        PenguinProportions.Profile profile=PenguinProportions.forModel(original);
        return profile==null?null:evaluate(action,clip,sample,parts,original,profile);
    }
    /**
     * Explicit-profile overload for callers that already resolved the dancer's profile. Imported
     * builtin dance clips use this so a formerly prone family (for example COW/SHEEP/PIG) keeps
     * the profile supplied by {@link PenguinDancePose} instead of recomputing a null forModel.
     * A null profile is a caller error and fails instead of silently succeeding.
     */
    public static PenguinAnimationPose evaluate(PenguinAction action,PenguinAnimationClip clip,
                                                PenguinAnimationClip.Sample sample,PenguinBodyModel parts,
                                                EntityModel<?> original,PenguinProportions.Profile profile) {
        if(profile==null)throw new IllegalArgumentException("explicit animation profile must not be null");
        return new PenguinAnimationPose(action,clip,sample,parts,original,profile);
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
        if(action==PenguinAction.FLYING_KICK||action==PenguinAction.DEATH){
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
