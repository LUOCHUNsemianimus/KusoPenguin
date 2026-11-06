package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.KusoPenguin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The five penguin animation roots: torso, two wings and two feet. The visible body is the loaded
 * low-poly mesh; these parts are empty controls that keep the existing bind pivots and animation
 * API. The head is intentionally absent and is drawn from the original renderer model.
 */
public final class PenguinBodyModel extends EntityModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "penguin_body"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart rightFoot;
    private final ModelPart leftFoot;

    public PenguinBodyModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.rightWing = root.getChild("right_wing");
        this.leftWing = root.getChild("left_wing");
        this.rightFoot = root.getChild("right_foot");
        this.leftFoot = root.getChild("left_foot");
    }

    public static LayerDefinition createBodyLayer() {
        // Empty controls only: the retired cube geometry and its generator are intentionally gone.
        // Pivots and part names must match PenguinMeshModel.EXPECTED_PIVOTS exactly.
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create(),
                PartPose.offset(5.45F, 2.6F, 0.2F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create(),
                PartPose.offset(-5.45F, 2.6F, 0.2F));
        root.addOrReplaceChild("right_foot", CubeListBuilder.create(),
                PartPose.offset(3.3F, 15.0F, 0.0F));
        root.addOrReplaceChild("left_foot", CubeListBuilder.create(),
                PartPose.offset(-3.3F, 15.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void resetPose() {
        this.root.getAllParts().forEach(ModelPart::resetPose);
    }

    public ModelPart body() {
        return this.body;
    }

    public ModelPart rightWing() {
        return this.rightWing;
    }

    public ModelPart leftWing() {
        return this.leftWing;
    }

    public ModelPart rightFoot() {
        return this.rightFoot;
    }

    public ModelPart leftFoot() {
        return this.leftFoot;
    }

    @Override
    public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Poses are adapted from the already-animated vanilla model in PenguinBodyLayer.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
