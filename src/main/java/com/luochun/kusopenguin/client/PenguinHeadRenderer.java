package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.luochun.kusopenguin.mixin.AgeableListModelAccessor;
import java.util.Iterator;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;

/**
 * Draws only the original head (including hat/nose/horns/ears/wool and head children) for a
 * supported entity. The caller supplies the original vertex consumer, light, overlay and color.
 *
 * <p>One shared head lookup is used by prone animals as well as the upright families. Quadruped
 * heads come from the protected {@code headParts()} invoker, so no child names are guessed; wolf
 * {@code real_head}, cow horns, pig snouts and cat nose/ears stay attached.
 */
public final class PenguinHeadRenderer {
    private PenguinHeadRenderer() {
    }

    /** Native head part for every supported upright or prone model family, or null. */
    static ModelPart headPart(EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> humanoid) {
            return humanoid.head;
        }
        if (model instanceof VillagerModel<?> villager) {
            return villager.getHead();
        }
        if (model instanceof CreeperModel<?> creeper) {
            return creeper.root().getChild("head");
        }
        if (model instanceof IronGolemModel<?> golem) {
            return golem.root().getChild("head");
        }
        if (model instanceof SpiderModel<?> spider) {
            return spider.root().getChild("head");
        }
        if (model instanceof AgeableListModel<?> ageable) {
            Iterator<ModelPart> heads = ((AgeableListModelAccessor) (Object) ageable)
                    .kusoPenguin$headParts().iterator();
            return heads.hasNext() ? heads.next() : null;
        }
        return null;
    }

    static Matrix4f headMatrix(EntityModel<?> model){
        PenguinProportions.Profile profile=PenguinProportions.forModel(model);
        if(profile==null)return new Matrix4f();
        PoseStack stack=new PoseStack();
        stack.translate(0,profile.headDropBlocks(),0);
        if(model instanceof WardenModel<?> warden){
            ModelPart root=warden.root(),bone=root.getChild("bone"),body=bone.getChild("body");
            root.translateAndRotate(stack);bone.translateAndRotate(stack);body.translateAndRotate(stack);
            body.getChild("head").translateAndRotate(stack);
        } else {
            ModelPart head=headPart(model);
            if(head==null)return new Matrix4f();
            head.translateAndRotate(stack);
        }
        return new Matrix4f(stack.last().pose());
    }

    
    /** Head-only native geometry bound, including noses, hats, horns and head children. */
    static float maxY(EntityModel<?> model,Matrix4f delta){
        PenguinProportions.Profile profile=PenguinProportions.forModel(model);
        if(profile==null)throw new IllegalArgumentException("Unsupported death head");
        PoseStack stack=new PoseStack();stack.mulPose(delta);
        stack.translate(0,profile.headDropBlocks(),0);
        ModelPart head;
        if(model instanceof WardenModel<?> warden){
            ModelPart root=warden.root(),bone=root.getChild("bone"),body=bone.getChild("body");
            root.translateAndRotate(stack);bone.translateAndRotate(stack);body.translateAndRotate(stack);
            head=body.getChild("head");
        }else{
            head=headPart(model);
            if(head==null)throw new IllegalArgumentException("Unsupported death head");
        }
        float[] max={Float.NEGATIVE_INFINITY};org.joml.Vector3f point=new org.joml.Vector3f();
        ModelPart.Visitor visitor=(pose,path,index,cube)->{
            for(int corner=0;corner<8;corner++){
                point.set((corner&1)==0?cube.minX:cube.maxX,(corner&2)==0?cube.minY:cube.maxY,(corner&4)==0?cube.minZ:cube.maxZ).div(16);
                pose.pose().transformPosition(point);max[0]=Math.max(max[0],point.y);
            }
        };
        head.visit(stack,visitor);
        if(model instanceof HumanoidModel<?> h)h.hat.visit(stack,visitor);
        return max[0];
    }

    public static boolean renderHead(EntityModel<?> model, PoseStack poseStack,
                                     VertexConsumer buffer, int packedLight,
                                     int packedOverlay, int color) {
        PenguinProportions.Profile profile = PenguinProportions.forModel(model);
        if (profile == null) {
            // Quadruped/spider families have no penguin proportion profile; they keep their native
            // head and are only translated by the prone head anchor.
            if (!PenguinPronePose.isProneModel(model)) {
                return false;
            }
            ModelPart head = headPart(model);
            if (head == null) {
                return false;
            }
            poseStack.pushPose();
            try {
                PenguinRenderContext.applyHead(poseStack);
                head.render(poseStack, buffer, packedLight, packedOverlay, color);
                return true;
            } finally {
                poseStack.popPose();
            }
        }
        // The head keeps its size and only moves down onto the scaled body. Villager profession
        // hats reach this helper through RenderLayerMixin, so they get the same single shift.
        poseStack.pushPose();
        try {
            PenguinRenderContext.applyHead(poseStack);
            poseStack.translate(0.0F, profile.headDropBlocks(), 0.0F);
            if (model instanceof HumanoidModel<?> humanoid) {
                humanoid.head.render(poseStack, buffer, packedLight, packedOverlay, color);
                humanoid.hat.render(poseStack, buffer, packedLight, packedOverlay, color);
            } else if (model instanceof WardenModel<?> warden) {
                ModelPart root = warden.root();
                ModelPart bone = root.getChild("bone");
                ModelPart body = bone.getChild("body");
                root.translateAndRotate(poseStack);
                bone.translateAndRotate(poseStack);
                body.translateAndRotate(poseStack);
                body.getChild("head").render(poseStack, buffer, packedLight, packedOverlay, color);
            } else {
                ModelPart head = headPart(model);
                if (head == null) {
                    return false;
                }
                head.render(poseStack, buffer, packedLight, packedOverlay, color);
            }
            return true;
        } finally {
            poseStack.popPose();
        }
    }
}
