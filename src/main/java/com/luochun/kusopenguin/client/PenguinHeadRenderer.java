package com.luochun.kusopenguin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;

/**
 * Draws only the original head (including hat/nose/head children) for a supported entity.
 * The caller supplies the original vertex consumer, light, overlay and color.
 */
public final class PenguinHeadRenderer {
    private PenguinHeadRenderer() {
    }

    static Matrix4f headMatrix(EntityModel<?> model){
        PoseStack stack=new PoseStack();
        stack.translate(0,PenguinProportions.forModel(model).headDropBlocks(),0);
        ModelPart head;
        if(model instanceof HumanoidModel<?> humanoid)head=humanoid.head;
        else if(model instanceof VillagerModel<?> villager)head=villager.getHead();
        else if(model instanceof CreeperModel<?> creeper)head=creeper.root().getChild("head");
        else if(model instanceof IronGolemModel<?> golem)head=golem.root().getChild("head");
        else if(model instanceof WardenModel<?> warden){
            ModelPart root=warden.root(),bone=root.getChild("bone"),body=bone.getChild("body");
            root.translateAndRotate(stack);bone.translateAndRotate(stack);body.translateAndRotate(stack);head=body.getChild("head");
        } else throw new IllegalArgumentException("No head contract for "+model.getClass());
        head.translateAndRotate(stack);return new Matrix4f(stack.last().pose());
    }

    
    /** Head-only native geometry bound, including noses, hats and head children. */
    static float maxY(EntityModel<?> model,Matrix4f delta){
        PoseStack stack=new PoseStack();stack.mulPose(delta);
        stack.translate(0,PenguinProportions.forModel(model).headDropBlocks(),0);
        ModelPart head;
        if(model instanceof HumanoidModel<?> h)head=h.head;
        else if(model instanceof VillagerModel<?> v)head=v.getHead();
        else if(model instanceof CreeperModel<?> c)head=c.root().getChild("head");
        else if(model instanceof IronGolemModel<?> g)head=g.root().getChild("head");
        else if(model instanceof WardenModel<?> w){
            ModelPart root=w.root(),bone=root.getChild("bone"),body=bone.getChild("body");
            root.translateAndRotate(stack);bone.translateAndRotate(stack);body.translateAndRotate(stack);head=body.getChild("head");
        }else throw new IllegalArgumentException("Unsupported death head");
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
            return false;
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
            } else if (model instanceof CreeperModel<?> creeper) {
                creeper.root().getChild("head").render(poseStack, buffer, packedLight, packedOverlay, color);
            } else if (model instanceof IronGolemModel<?> golem) {
                golem.root().getChild("head").render(poseStack, buffer, packedLight, packedOverlay, color);
            } else {
                ((VillagerModel<?>) model).getHead()
                        .render(poseStack, buffer, packedLight, packedOverlay, color);
            }
            return true;
        } finally {
            poseStack.popPose();
        }
    }
}
