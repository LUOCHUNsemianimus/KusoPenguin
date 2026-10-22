package com.luochun.kusopenguin.mixin;

import com.github.tartaricacid.touhoulittlemaid.compat.gun.common.GunClientUtil;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.PenguinRenderSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Touhou Little Maid held-item bridge. The target mod is a compileOnly dependency, so
 * this pseudo mixin is silently skipped when it is absent. TLM's native main/off-hand loop,
 * display-context selection, left-hand flag and CarryOn checks stay untouched; only the inner
 * {@code renderArmWithItem} draw is replaced for a ready standard-bone maid penguin.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidHeldItem")
public abstract class MaidHeldItemLayerMixin {
    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void kusoPenguin$renderOnWing(Mob maid, ItemStack itemStack,
                                          ItemDisplayContext transformType, HumanoidArm handSide,
                                          PoseStack poseStack, MultiBufferSource buffer,
                                          int packedLight, CallbackInfo ci) {
        if (itemStack.isEmpty()) {
            return;
        }
        EntityModel<?> parentModel = ((RenderLayer<?, ?>) (Object) this).getParentModel();
        if (!PenguinRenderSupport.isMaidReplacement(maid, parentModel)) {
            return;
        }
        poseStack.pushPose();
        try {
            if (!PenguinRenderContext.applyHand(poseStack, handSide)) {
                return;
            }
            boolean left = handSide == HumanoidArm.LEFT;
            // TLM's native -0.525 Z convention was authored for its Bedrock hand frame. The
            // shared penguin helper pre-subtracts vanilla's -0.625 Z residual, so reproduce that
            // exact vanilla tail here to land the item on the wing without changing its size.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            GunClientUtil.addItemTranslate(poseStack, itemStack, left);
            this.itemInHandRenderer.renderItem(maid, itemStack, transformType, left, poseStack,
                    buffer, packedLight);
            ci.cancel();
        } finally {
            poseStack.popPose();
        }
    }
}
