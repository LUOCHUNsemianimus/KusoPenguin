package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.animation.PenguinKick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional TLM boundary. EntityMaid.doHurtTarget runs its native Pre event, baubles and the whole
 * weapon/sweep/damage override before Mob.super, so a later guard would already have fired them.
 * Cancelling at HEAD blocks that ordinary override only while the shared kick owns this maid.
 * During the kick dispatch MOB_STRIKE makes blockOrdinaryMelee return false, so the native dynamic
 * override (PRE/POST/baubles/durability/sweep/enchant/damage) remains the one attack path.
 *
 * <p>The target class is a string and no optional maid API type appears in this mixin, so the
 * class is skipped when TLM is absent.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")
public abstract class MaidMeleeAttackMixin {
    @Inject(method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void kusoPenguin$blockOrdinaryMaidMelee(Entity target, CallbackInfoReturnable<Boolean> ci) {
        if (PenguinKick.blockOrdinaryMelee((Mob) (Object) this)) {
            ci.setReturnValue(false);
        }
    }
}
