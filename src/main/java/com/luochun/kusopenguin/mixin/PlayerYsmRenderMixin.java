package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.luochun.kusopenguin.client.compat.YsmCompatClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Optional YSM 2.6.5 player renderer frame.
 *
 * <p>The pinned player renderer resolves its active YSM model and delegates the whole actual native
 * player draw to this shared high-level method. Wrapping that call (not vanilla
 * {@code LivingEntityRenderer.render}) puts one penguin frame around the exact native head/body
 * pass and all registered native layers. The wrapper's inherited entity accessor must report an
 * actual {@link net.minecraft.world.entity.player.Player}; every other entity or inactive effect
 * keeps the complete native draw.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OO0OOoo0ooooOoO0O0o00Ooo")
public abstract class PlayerYsmRenderMixin {
    private static final String RENDER =
            "oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;"
                    + "Lnet/minecraft/resources/ResourceLocation;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @WrapMethod(method = RENDER, remap = false)
    private void kusoPenguin$wrapPlayerFrame(@Coerce Object wrapper, ResourceLocation texture,
                                             float entityYaw, float partialTick,
                                             PoseStack poseStack,
                                             MultiBufferSource bufferSource, int packedLight,
                                             Operation<Void> original) {
        YsmCompatClient.enterPlayer(wrapper, poseStack, bufferSource, packedLight, partialTick);
        boolean normal = false;
        try {
            original.call(wrapper, texture, entityYaw, partialTick, poseStack, bufferSource,
                    packedLight);
            normal = true;
        } finally {
            // Pop/replay exactly once on normal return and on every native exception.
            YsmCompatClient.exit(normal);
        }
    }
}
