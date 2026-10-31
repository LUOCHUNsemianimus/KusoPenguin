package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.luochun.kusopenguin.animation.PenguinAction;
import com.luochun.kusopenguin.client.compat.GeoMaidView;
import com.luochun.kusopenguin.client.compat.IronCastingCompatClient;
import com.luochun.kusopenguin.client.compat.NativeCastingView;
import com.luochun.kusopenguin.client.compat.PenguinMelodiesCompatClient;
import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.luochun.kusopenguin.client.dance.DanceClient;
import com.luochun.kusopenguin.compat.MelodiesDanceCompat;
import com.luochun.kusopenguin.dance.DanceLibrary;
import com.luochun.kusopenguin.dance.Source;
import java.util.ArrayDeque;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/** Nestable render scope: all head/body/decorative passes share one evaluated entity pose. */
public final class PenguinRenderContext {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES=ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<PreviewFrame> PREVIEW=new ThreadLocal<>();
    private static PenguinBodyModel shared;
    private static final float PIXELS_PER_BLOCK=16.0F;
    private record PreviewFrame(LivingEntity entity,String clipId,PenguinAnimationClip clip,float age){}
    /** Caller-owned preview scope: push before the detached draw, close in a finally block. */
    public static final class PreviewScope implements AutoCloseable {
        private final PreviewFrame previous;
        private PreviewScope(PreviewFrame previous){this.previous=previous;}
        @Override public void close(){if(previous==null)PREVIEW.remove();else PREVIEW.set(previous);}
    }
    private static final class Frame {
        final LivingEntity entity;final EntityModel<?> model;final float partial;
        boolean evaluated;PenguinAnimationPose pose;
        boolean postureEvaluated;PenguinPlayerPosture posture;
        boolean danceEvaluated,dancing;
        boolean castingEvaluated;IronCastingCompatClient.Snapshot casting;
        boolean melodiesEvaluated;PenguinMelodiesCompatClient.Snapshot melodies;
        Frame(LivingEntity entity,EntityModel<?> model,float partial){this.entity=entity;this.model=model;this.partial=partial;}
    }
    private PenguinRenderContext(){}
    public static void setModel(PenguinBodyModel model){shared=model;}
    /** Shared five-part body model used by the Gecko renderer's body entry point. */
    public static PenguinBodyModel sharedModel(){return shared;}
    /** Model proxy of the current draw, used by optional Gecko layer hooks. */
    public static EntityModel<?> currentModel(){
        Frame f=FRAMES.get().peek();return f==null?null:f.model;
    }
    public static void push(LivingEntity entity,EntityModel<?> model,float partial){FRAMES.get().push(new Frame(entity,model,partial));}
    public static void pop(){ArrayDeque<Frame> frames=FRAMES.get();frames.pop();if(frames.isEmpty())FRAMES.remove();}
    /**
     * Scoped client-only preview override. The detached entity and clip are visible only to the
     * current thread/draw; the ordinary dance selection below this scope is unchanged.
     */
    public static PreviewScope pushPreview(LivingEntity entity,String clipId,PenguinAnimationClip clip,float age){
        PreviewFrame previous=PREVIEW.get();
        PREVIEW.set(new PreviewFrame(entity,clipId,clip,age));
        return new PreviewScope(previous);
    }
    private static PreviewFrame previewFrame(LivingEntity entity){
        PreviewFrame preview=PREVIEW.get();
        return preview!=null&&preview.entity==entity?preview:null;
    }
    static PenguinPlayerPosture posture(){
        Frame f=FRAMES.get().peek();if(f==null)return null;
        if(f.postureEvaluated)return f.posture;f.postureEvaluated=true;
        if(shared==null||!PenguinRenderSupport.shouldReplace(f.entity))return null;
        f.posture=PenguinPlayerPosture.of(f.entity,f.model,PenguinProportions.forModel(f.model),shared);
        return f.posture;
    }
    /** Per-draw ownership: death and an active kick/native posture outrank the music. */
    public static boolean isDanceFrame(LivingEntity entity){
        Frame f=FRAMES.get().peek();
        if(f==null||f.entity!=entity)return false;
        if(f.danceEvaluated)return f.dancing;
        f.danceEvaluated=true;
        // Detached preview shares the viewer's skin/profile UUID, not their live action state.
        if(previewFrame(entity)!=null)return f.dancing=PenguinMeshPreview.isEnabled()
                &&PenguinRenderSupport.shouldReplace(entity);
        // Sample the optional Iron casting gate before the dance decision. While the Iron layer
        // (including its fade) is active, casting owns the pose, so the music dance must not also
        // claim the draw and prepareBody must still run the adapter/posture corrections.
        if(casting()!=null)return f.dancing=false;
        // Native Iron caster view: native Geo bones already own the animation. Never replace a
        // caster clip with the Kuso taunt/kick/dance selection.
        if(f.model instanceof NativeCastingView view&&view.kusoPenguin$ready())
            return f.dancing=false;
        if(!PenguinMeshPreview.isEnabled()||!PenguinRenderSupport.shouldReplace(entity)
                ||PenguinDeathClient.rendering(entity))return false;
        // A live instrument-holding snapshot outranks the optional dance/taunt rig. The snapshot
        // owns the wing pose and, for Geo/YSM, the supplemental head delta for this frame.
        if(activeFlyingKick(f))return false;
        // Only an actually PLAYING instrument blocks an explicit dance. A merely held/paused
        // instrument keeps its ordinary idle pose when there is no dance, but must not hide an
        // authoritative manual/maid-music/jukebox/revival selection.
        if(MelodiesDanceCompat.playingInstrument(f.entity))return false;
        boolean preview=previewFrame(entity)!=null;
        boolean revival=com.luochun.kusopenguin.client.revival.RevivalClientState.pole(entity);
        if(!preview&&!revival&&!com.luochun.kusopenguin.client.music.DiscClient.dancing(entity)
                &&!DanceClient.dancing(entity))return false;
        PenguinPlayerPosture p=posture();
        return f.dancing=p==null||!p.suppressesSpecialClips();
    }
    private static boolean activeFlyingKick(Frame f){
        PenguinAction action=PenguinPoseClient.action(f.entity);
        if(action!=PenguinAction.FLYING_KICK)return false;
        PenguinAnimationClip clip=PenguinMeshModel.animationClip(action);
        float age=PenguinPoseClient.age(f.entity,action,f.partial);
        return clip!=null&&age>=0&&age<clip.totalDuration();
    }
    /**
     * One cached instrument snapshot per draw, after death, kick, native/Iron casting and
     * non-standing posture have kept their priority. Every null path leaves the ordinary prepared
     * mesh and all native matrices untouched.
     */
    static PenguinMelodiesCompatClient.Snapshot melodies(){
        Frame f=FRAMES.get().peek();
        if(f==null)return null;
        if(f.melodiesEvaluated)return f.melodies;
        f.melodiesEvaluated=true;
        if(shared==null||!PenguinMeshPreview.isEnabled()
                ||!PenguinRenderSupport.shouldReplace(f.entity)
                ||PenguinDeathClient.rendering(f.entity))return null;
        if(f.model instanceof NativeCastingView view&&view.kusoPenguin$ready())return null;
        if(!PenguinMelodiesCompatClient.holdingInstrument(f.entity))return null;
        if(casting()!=null)return null;
        if(activeFlyingKick(f))return null;
        PenguinPlayerPosture posture=posture();
        if(posture!=null&&posture.suppressesSpecialClips())return null;
        if(PenguinProportions.forModel(f.model)==null
                ||PenguinPoseAdapters.find(f.model)==null)return null;
        // Only an actual YSM maid render proxy lacks the native player IM progress update. Every
        // other supported model keeps its original clock and passes false.
        f.melodies=PenguinMelodiesCompatClient.snapshot(f.entity,
                YsmCompatClient.isView(f.model));
        return f.melodies;
    }
    /**
     * One optional Iron casting snapshot per draw. Death and an active FLYING_KICK keep priority;
     * otherwise a live Iron layer (including fade) replaces taunt/music dance only while the iron
     * mod, its associated {@code irons_spellbooks:animation} layer and the PlayerAnimator applier
     * are all active. Returns null for non-players, no penguin effect, or missing optional classes.
     */
    static IronCastingCompatClient.Snapshot casting(){
        Frame f=FRAMES.get().peek();
        if(f==null)return null;
        if(f.castingEvaluated)return f.casting;
        f.castingEvaluated=true;
        if(!(f.entity instanceof AbstractClientPlayer player)
                ||!PenguinMeshPreview.isEnabled()
                ||!PenguinRenderSupport.shouldReplace(f.entity)
                ||PenguinDeathClient.rendering(f.entity))return null;
        IronCastingCompatClient.Snapshot snapshot=IronCastingCompatClient.snapshot(player);
        if(snapshot==null)return null;
        PenguinAction action=PenguinPoseClient.action(f.entity);
        if(action==PenguinAction.DEATH)return null;
        if(action==PenguinAction.FLYING_KICK){
            PenguinAnimationClip clip=PenguinMeshModel.animationClip(action);
            float age=PenguinPoseClient.age(f.entity,action,f.partial);
            if(clip!=null&&age>=0&&age<clip.totalDuration())return null;
        }
        f.casting=snapshot;
        return snapshot;
    }
    /** True only while the current draw actually uses the optional casting pose. */
    public static boolean isCasting(LivingEntity entity){
        Frame f=FRAMES.get().peek();
        return f!=null&&f.entity==entity&&casting()!=null;
    }
    /**
     * Item-channel access for the held-item layer through the same one-draw casting snapshot. It
     * returns null when casting is inactive, so the caller must keep the complete original method.
     * The channel contains final native scale/position/rotation and is never re-read after this.
     */
    public static IronCastingCompatClient.ItemChannel castingItemChannel(HumanoidArm arm){
        IronCastingCompatClient.Snapshot snapshot=casting();
        if(snapshot==null)return null;
        return arm==HumanoidArm.LEFT?snapshot.leftItem():snapshot.rightItem();
    }
    /**
     * Removes PlayerAnimator's native upper-layer body bend for the active casting frame. The
     * caller owns the returned push/pop; false means no frame was pushed. This keeps headgear,
     * body and held-item layers on one body frame so the cast pose cannot double the bend.
     */
    public static boolean pushCastingLayer(PoseStack poseStack,Object layer){
        IronCastingCompatClient.Snapshot snapshot=casting();
        if(snapshot==null||!snapshot.bendEnabled())return false;
        IronCastingCompatClient.Bend body=snapshot.layerBody();
        return IronCastingCompatClient.pushInverseBodyBend(poseStack,layer,
                body.direction(),body.amount());
    }
    /** Prone families borrow an upright 2.5-head profile only for the current dance draw. */
    public static PenguinProportions.Profile profile(EntityModel<?> model){
        Frame f=FRAMES.get().peek();
        // Wool and collar passes can supply a second model instance for the same draw.
        return f!=null&&isDanceFrame(f.entity)
                ?PenguinProportions.dancingForModel(model):PenguinProportions.forModel(model);
    }
    /**
     * Single reset/adapt/correct entry point for the shared body. The player posture applies after
     * the adapter so generic mob adapters and the animation rig keep their previous transforms.
     */
    static void prepareBody(PenguinBodyModel penguin){
        Frame f=FRAMES.get().peek();
        penguin.resetPose();
        if(f==null)return;
        if(isDanceFrame(f.entity))return; // Procedural rig owns all joints, not the walk adapter.
        // Prone animals own a frame-independent bind pose: no leg/wing walk cycle and no humanoid
        // clip may ever move their limbs while sliding.
        if(PenguinRenderSupport.isProne(f.entity)){
            PenguinPronePose.applyStatic(penguin);
            return;
        }
        PenguinPoseAdapter adapter=PenguinPoseAdapters.find(f.model);
        if(adapter==null)return;
        adapter.apply(penguin,f.model,f.entity);
        PenguinPlayerPosture posture=posture();
        if(posture!=null)posture.applyCorrections(penguin,f.model);
        // Instrument arm angles are deliberately applied after adapter/posture corrections so the
        // snapshot owns only the wing rotations and never disturbs prepared pivots or scales.
        PenguinMelodiesCompatClient.Snapshot instrument=melodies();
        if(instrument!=null){
            instrument.applyTo(penguin);
            alignPerformingMaid(penguin,f);
        }
    }
    /**
     * Maid models may animate the head locator through parents or head-local translation.
     * Our adapters copy local limb values, whereas the retained head walks that complete chain.
     * Anchor the whole prepared assembly to its live neck, in the same canonical block frame.
     * Subtract the already-adapted body offset before converting to penguin pixels: otherwise
     * body bob would be applied twice. Rotations remain native/IM-owned, not inferred from pitch.
     * Body and both hand mounts call prepareBody, so they receive this identical correction.
     */
    private static void alignPerformingMaid(PenguinBodyModel penguin,Frame f){
        if(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(f.entity.getType())
                .equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("touhou_little_maid","maid")))return;
        PenguinProportions.Profile profile=profile(f.model);
        if(profile==null)return;
        Matrix4f head=PenguinHeadRenderer.headMatrix(f.model);
        float scale=profile.bodyScale();
        net.minecraft.client.model.geom.ModelPart body=penguin.body();
        float dx=head.m30()*16/scale-body.x;
        float dy=(head.m31()-profile.bodyOffsetBlocks())*16/scale-body.y;
        float dz=head.m32()*16/scale-body.z;
        for(net.minecraft.client.model.geom.ModelPart part:new net.minecraft.client.model.geom.ModelPart[]{
                body,penguin.rightWing(),penguin.leftWing(),penguin.rightFoot(),penguin.leftFoot()}){
            part.x+=dx;part.y+=dy;part.z+=dz;
        }
    }
    /**
     * Additional translation from the penguin body origin to the rounded upper back, in penguin
     * model pixels. Vanilla {@code ElytraLayer} then adds its own +0.125 blocks in this same
     * scaled frame; at the humanoid bodyScale of .5 that is two more penguin pixels, so the final
     * pivot is about four pixels behind the body centre. Keeping our own constant separate avoids
     * applying that native offset twice.
     */
    private static final float ELYTRA_PIVOT_BACK_PIXELS=2.0F;
    /**
     * Re-mounts the still-native Elytra layer on the shared penguin body frame for a replaced,
     * actually gliding player. The caller owns push/finally-pop; this method only appends the
     * mount transform and returns true. It returns false without touching the stack for every
     * other draw, so vanilla, death-copy and non-gliding hidden-Elytra paths keep their policy.
     *
     * <p>Transform order matches {@code PenguinBodyLayer}: profile body offset, body scale, the
     * prepared {@code body} ModelPart frame, then the named back offset. It adds no global
     * rotation and no second Elytra render: {@code PlayerRenderer.setupRotations} still owns the
     * fall-flying pitch/yaw/roll, and vanilla {@code ElytraLayer} still owns equipment
     * eligibility, texture/cape/custom-Elytra selection, glint and wing animation.
     */
    public static boolean mountElytraOnBack(PoseStack poseStack){
        Frame f=FRAMES.get().peek();
        if(f==null||shared==null||!PenguinRenderSupport.shouldMountElytraOnBack(f.entity))return false;
        PenguinProportions.Profile profile=PenguinProportions.forModel(f.model);
        if(profile==null)return false;
        prepareBody(shared);
        poseStack.translate(0.0F,profile.bodyOffsetBlocks(),0.0F);
        poseStack.scale(profile.bodyScale(),profile.bodyScale(),profile.bodyScale());
        shared.body().translateAndRotate(poseStack);
        poseStack.translate(0.0F,0.0F,ELYTRA_PIVOT_BACK_PIXELS/PIXELS_PER_BLOCK);
        return true;
    }
    /**
     * Shared 16-joint factory bridge for optional native views outside this package. The matrices
     * are absolute penguin-model-pixel transforms and the head delta is already rigid.
     */
    public static PenguinAnimationPose fromNativeRig(Matrix4f[] world, Matrix4f headDelta,
                                                     PenguinAnimationClip bind){
        return PenguinAnimationPose.fromDance(world,headDelta,bind);
    }
    public static PenguinAnimationPose pose(){
        Frame f=FRAMES.get().peek();if(f==null)return null;
        if(f.evaluated)return f.pose;f.evaluated=true;
        if(shared==null||!PenguinMeshPreview.isEnabled()||!PenguinRenderSupport.shouldReplace(f.entity))return null;
        // Native Iron caster: build the shared 16-joint pose from the already-animated Geo bones.
        // This runs before dance/taunt/kick selection and never re-runs native handleAnimations.
        if(f.model instanceof NativeCastingView view&&view.kusoPenguin$ready()){
            prepareBody(shared);
            f.pose=view.kusoPenguin$nativePose(shared,profile(f.model));
            return f.pose;
        }
        // isDanceFrame sets danceEvaluated before it consults the instrument snapshot, so the
        // posture/profile lookup inside melodies() cannot re-enter an undecided dance frame.
        if(isDanceFrame(f.entity)){
            prepareBody(shared);
            f.pose=dancePose(f);
            return f.pose;
        }
        // Instrument holding is an ordinary prepared mesh draw, not a taunt/dance/action pose.
        if(melodies()!=null)return null;
        // The effect-add path may publish TAUNT. Prone animals never evaluate humanoid/action
        // clips; returning null keeps their static slide pose and leaves the server action sets
        // untouched.
        if(PenguinRenderSupport.isProne(f.entity))return null;
        // Optional Iron casting owns taunt/music dance while its modifier layer is live. Death and
        // an active FLYING_KICK were already excluded by casting(), so priority is unchanged.
        IronCastingCompatClient.Snapshot casting=casting();
        if(casting!=null){
            prepareBody(shared);
            f.pose=PenguinCastingPose.evaluate(shared,f.model,profile(f.model),casting);
            return f.pose;
        }
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
    /**
     * Single dance selection entry shared by players, prone animals and all three maid paths.
     * Revival remains the authoritative client gate and outranks every new selection; death, kick,
     * casting, actual performance and non-standing posture were already rejected by
     * {@link #isDanceFrame(LivingEntity)}.
     */
    private static PenguinAnimationPose dancePose(Frame f){
        PenguinProportions.Profile profile=profile(f.model);
        PreviewFrame preview=previewFrame(f.entity);
        if(preview!=null)return previewPose(preview,f,profile);
        if(com.luochun.kusopenguin.client.revival.RevivalClientState.pole(f.entity)){
            PenguinAnimationPose custom=playbackPose(f,Source.REVIVAL);
            if(custom!=null)return custom;
            return PenguinDancePose.evaluatePole(
                    com.luochun.kusopenguin.client.revival.RevivalClientState.elapsed(f.entity,f.partial),
                    shared,f.model,profile);
        }
        String clipId=DanceClient.clip(f.entity);
        if(clipId!=null){
            float elapsed=DanceClient.elapsed(f.entity,f.partial);
            if(DanceLibrary.isBuiltin(clipId)){
                int style=builtinStyle(clipId);
                return style<0
                        ?PenguinDancePose.evaluate(elapsed,shared,f.model,profile)
                        :PenguinDancePose.evaluateStyle(style,elapsed,shared,f.model,profile);
            }
            PenguinAnimationPose custom=playbackClip(f,clipId,elapsed);
            if(custom!=null)return custom;
        }
        // Legacy fallback for a Disc.Dance membership whose new Playback has not arrived yet.
        return PenguinDancePose.evaluate(
                com.luochun.kusopenguin.client.music.DiscClient.danceElapsed(f.entity,f.partial),
                shared,f.model,profile);
    }
    /** Preview uses exactly the same builtin/penguin-clip evaluators as authoritative playback. */
    private static PenguinAnimationPose previewPose(PreviewFrame preview,Frame f,
                                                     PenguinProportions.Profile profile){
        String clipId=preview.clipId();
        float age=preview.age();
        if(DanceLibrary.isBuiltin(clipId)){
            int style=builtinStyle(clipId);
            return style<0
                    ?PenguinDancePose.evaluate(age,shared,f.model,profile)
                    :PenguinDancePose.evaluateStyle(style,age,shared,f.model,profile);
        }
        PenguinAnimationClip clip=preview.clip();
        if(clip==null)return null;
        float duration=clip.totalDuration();
        if(!(duration>0.0F)||!Float.isFinite(age))return null;
        float wrapped=age%duration;
        if(wrapped<0.0F)wrapped+=duration;
        PenguinAnimationClip.Sample sample=clip.sample(wrapped);
        if(sample.weight()<=0.0F)return null;
        return PenguinAnimationPose.evaluate(PenguinAction.TAUNT,clip,sample,shared,f.model);
    }
    private static PenguinAnimationPose playbackPose(Frame f,Source source){
        if(!DanceClient.active(f.entity,source))return null;
        return playbackClip(f,DanceClient.clip(f.entity),DanceClient.elapsed(f.entity,f.partial));
    }
    /** Uploaded clips reuse the fixed 16-joint evaluate path and loop on the total duration. */
    private static PenguinAnimationPose playbackClip(Frame f,String clipId,float elapsed){
        if(clipId==null||clipId.isEmpty()||DanceLibrary.isBuiltin(clipId))return null;
        PenguinAnimationClip clip=DanceClient.cachedClip(clipId);
        if(clip==null)return null;
        float duration=clip.totalDuration();
        if(!(duration>0.0F)||!Float.isFinite(elapsed))return null;
        float age=elapsed%duration;
        if(age<0.0F)age+=duration;
        PenguinAnimationClip.Sample sample=clip.sample(age);
        if(sample.weight()<=0.0F)return null;
        return PenguinAnimationPose.evaluate(PenguinAction.TAUNT,clip,sample,shared,f.model);
    }
    private static int builtinStyle(String clipId){
        if(DanceLibrary.BUILTIN_HIP.equals(clipId))return 0;
        if(DanceLibrary.BUILTIN_POLE.equals(clipId))return 1;
        if(DanceLibrary.BUILTIN_WAVE.equals(clipId))return 2;
        return -1; // builtin:cycle keeps the original five-second style transitions.
    }
    public static void applyHead(PoseStack stack){
        Frame f=FRAMES.get().peek();
        if(f!=null&&PenguinRenderSupport.isProne(f.entity)){
            if(shared!=null){
                // The head is anchored to the same prepared static body and mesh-tier floor bound
                // that the body layer draws, so both use one neck origin equation.
                prepareBody(shared);
                PenguinMeshModel mesh=PenguinMeshPreview.isEnabled()
                        ? PenguinMeshModel.current() : null;
                PenguinPronePose.applyHead(stack,f.model,shared,mesh);
            }
            return;
        }
        PenguinAnimationPose p=pose();
        if(p!=null){stack.mulPose(p.headDelta);return;}
        PenguinPlayerPosture posture=posture();
        if(posture!=null)posture.applyHead(stack);
        if(f!=null){
            PenguinMelodiesCompatClient.Snapshot instrument=melodies();
            if(instrument!=null){
                // Player/Bedrock already receive IM's head channels through the native mixin;
                // Geo/YSM native heads need the same shared rigid supplement here.
                Matrix4f delta=instrument.headDelta(PenguinHeadRenderer.headMatrix(f.model),
                        f.model instanceof GeoMaidView);
                if(delta!=null)stack.mulPose(delta);
            }
        }
    }
    /**
     * Reapplies the complete canonical head transform in the native Gecko frame.
     *
     * <p>The native head/head-gear layer walks Gecko head locators, so the canonical
     * {@code Delta * Tdrop} must be conjugated by C:
     * {@code C * Delta * Tdrop * C^-1}. The drop is part of the transform because the main head
     * pass also draws the native head under the same drop. The caller owns push/finally-pop.
     */
    public static void applyGeoHead(PoseStack stack){
        Frame f=FRAMES.get().peek();
        if(f==null)return;
        PenguinProportions.Profile profile=profile(f.model);
        float drop=profile==null?0:profile.headDropBlocks();
        stack.scale(-1,-1,1);
        stack.translate(0,-1.5F,0);
        applyHead(stack);
        stack.translate(0,drop,0);
        stack.translate(0,1.5F,0);
        stack.scale(-1,-1,1);
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
        // Instrument frames only: move the exact lute/vielle forward along canonical -Z (entity
        // front) in this parent frame, before the held renderer applies the rotated item frame.
        // Other items, ordinary weapons and every null-snapshot path add exact zero and therefore
        // no matrix operation.
        if(melodies()!=null){
            float forwardPixels=PenguinMelodiesCompatClient.heldItemForwardPixels(f.entity,arm);
            if(forwardPixels!=0.0F){
                stack.translate(0.0F,0.0F,-forwardPixels*profile.bodyScale()/PIXELS_PER_BLOCK);
            }
        }
        PenguinHeldItemRenderer.apply(stack,profile,animated,shared,arm);
        return true;
    }
}
