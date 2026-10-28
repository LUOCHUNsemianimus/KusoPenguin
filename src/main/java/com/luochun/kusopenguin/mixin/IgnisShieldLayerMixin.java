package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.IgnisCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Both shield and damage cracks use a distinct, freshly animated Ignis model. */
@Pseudo
@Mixin(targets="com.github.L_Ender.cataclysm.client.render.layer.Ignis_Shield_Layer",remap=false)
public abstract class IgnisShieldLayerMixin {
    @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignis_Entity;FFFFFF)V",
            at=@At(value="INVOKE",target="Lcom/github/L_Ender/cataclysm/client/model/entity/Ignis_Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"),require=2)
    private void kusoPenguin$shield(@Coerce EntityModel<?> model,PoseStack stack,
            VertexConsumer buffer,int light,int overlay,PoseStack originalStack,
            MultiBufferSource buffers,int originalLight,@Coerce LivingEntity entity,
            float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!IgnisCompatClient.renderShieldPass(entity,model,stack,buffer,light,overlay,-1)) {
            model.renderToBuffer(stack,buffer,light,overlay);
        }
    }
}
