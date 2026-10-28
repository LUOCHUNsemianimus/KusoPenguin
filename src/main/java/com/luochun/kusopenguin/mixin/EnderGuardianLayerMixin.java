package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.client.compat.EnderGuardianCompatClient;
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

/**
 * Ender Guardian's own glow layer submits the parent model directly. Only that single concrete
 * call is redirected; the native {@code deathTime < 100} condition and eye render type stay
 * untouched. Any non-guardian draw replays the original four-argument model call.
 */
@Pseudo
@Mixin(targets="com.github.L_Ender.cataclysm.client.render.layer.Ender_Guardian_Layer",remap=false)
public abstract class EnderGuardianLayerMixin {
    @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ender_Guardian_Entity;FFFFFF)V",
            at=@At(value="INVOKE",target="Lcom/github/L_Ender/cataclysm/client/model/entity/Ender_Guardian_Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"),require=1)
    private void kusoPenguin$guardianGlow(@Coerce EntityModel<?> model,PoseStack stack,
            VertexConsumer buffer,int light,int overlay,PoseStack originalStack,
            MultiBufferSource buffers,int originalLight,@Coerce LivingEntity entity,
            float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!EnderGuardianCompatClient.renderGlowPass(entity,model,stack,buffer,light,overlay,-1)) {
            model.renderToBuffer(stack,buffer,light,overlay);
        }
    }
}
