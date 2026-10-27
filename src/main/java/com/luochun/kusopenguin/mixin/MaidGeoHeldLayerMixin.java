package com.luochun.kusopenguin.mixin;

import com.github.tartaricacid.touhoulittlemaid.compat.carryon.RenderFixer;
import com.github.tartaricacid.touhoulittlemaid.compat.gun.common.GunClientUtil;
import com.luochun.kusopenguin.client.PenguinRenderContext;
import com.luochun.kusopenguin.client.compat.MaidGeoCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * Optional held-item bridge for the Gecko replacement.
 *
 * <p>TLM's native held layer walks hand locators and may also draw extra hand bones. For a
 * successfully replaced Gecko maid this hook owns the single main/off-hand draw, so the live item
 * lands on the shared penguin wing exactly once and duplicate extra hands are never drawn. The
 * native display context and left-hand flag are reused. The full-layer override also reproduces
 * TLM's {@code RenderFixer.isCarryOnRender} check before each hand, because the native loop that
 * normally performs it is bypassed.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld")
public abstract class MaidGeoHeldLayerMixin {
    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/"
                    + "Mob;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kusoPenguin$renderGeoHeld(PoseStack poseStack, MultiBufferSource buffer,
                                           int packedLight, Mob entity, float limbSwing,
                                           float limbSwingAmount, float partialTick,
                                           float ageInTicks, float netHeadYaw, float headPitch,
                                           CallbackInfo ci) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) {
            return;
        }
        EntityModel<?> view = PenguinRenderContext.currentModel();
        if (!MaidGeoCompatClient.isReplacement(view, entity)) {
            return;
        }
        poseStack.pushPose();
        try {
            if (!main.isEmpty() && !RenderFixer.isCarryOnRender(main, buffer)) {
                draw(entity, main, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT,
                        poseStack, buffer, packedLight);
            }
            if (!off.isEmpty() && !RenderFixer.isCarryOnRender(off, buffer)) {
                draw(entity, off, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
                        poseStack, buffer, packedLight);
            }
        } finally {
            poseStack.popPose();
        }
        ci.cancel();
    }

    /** One canonical wing frame: C, the shared hand helper, then the native TLM item tail. */
    private void draw(Mob entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                      PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        try {
            // Canonical penguin model frame -> native Gecko frame.
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.5F, 0.0F);
            if (!PenguinRenderContext.applyHand(poseStack, arm)) {
                return;
            }
            boolean left = arm == HumanoidArm.LEFT;
            // TLM's native -0.525/-0.1 convention was authored for its Gecko hand frame. The
            // shared helper pre-subtracts vanilla's fixed hand offset; reproduce that exact
            // vanilla tail so the live item lands on the wing without changing its size.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            GunClientUtil.addItemTranslate(poseStack, stack, left);
            // Return the complete canonical placement to the native layer frame as
            // C * hand * tail * C^-1, so item geometry keeps its normal upright orientation.
            poseStack.translate(0.0F, 1.5F, 0.0F);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            this.itemInHandRenderer.renderItem(entity, stack, context, left, poseStack, buffer,
                    packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
