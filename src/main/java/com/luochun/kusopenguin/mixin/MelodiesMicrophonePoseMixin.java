package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.vocals.VocalItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a held microphone the animator choice before IM's native progress/instrument scan.
 *
 * <p>This only changes which item animator runs. The microphone animator itself is registered
 * through {@code MicrophonePose}; a paused or unconfigured microphone still gets the chest pose,
 * while every entity without a microphone keeps IM's original choice untouched.</p>
 */
@Pseudo
@Mixin(targets = "immersive_melodies.client.animation.EntityModelAnimator", remap = false)
public abstract class MelodiesMicrophonePoseMixin {
    @Inject(method = "getInstrument", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kuso$preferHeldMicrophone(LivingEntity entity,
                                                  CallbackInfoReturnable<Item> callback) {
        if (entity == null) {
            return;
        }
        ItemStack main = entity.getMainHandItem();
        if (VocalItems.isMicrophone(main)) {
            callback.setReturnValue(main.getItem());
            return;
        }
        ItemStack off = entity.getOffhandItem();
        if (VocalItems.isMicrophone(off)) {
            callback.setReturnValue(off.getItem());
        }
    }
}
