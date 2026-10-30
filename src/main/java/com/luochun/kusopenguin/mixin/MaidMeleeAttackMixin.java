package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.animation.PenguinKick;
import com.luochun.kusopenguin.compat.MaidKickCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional TLM melee boundary.
 *
 * <p>TLM's native {@code isWithinMeleeAttackRange} uses the interaction-range attribute plus a
 * favorability bonus against center distance, so a maid can reach several blocks without the two
 * boxes touching. The brain task swings and starts its cooldown as soon as that range check passes,
 * before {@code doHurtTarget}. A penguin maid therefore overrides that intent check with the shared
 * physical-contact helper, and a second HEAD gate protects direct ordinary {@code doHurtTarget}
 * calls before TLM's Pre event or baubles. Non-penguin maids are untouched.
 *
 * <p>The shared kick's own MOB_STRIKE dispatch is not ordinary melee: it is identified by
 * {@link PenguinKick#dispatching(Mob)} and keeps the native dynamic damage/Pre/Post path. The
 * kick's per-step flight sweep is the real contact there, never the maid's four-block native range.
 *
 * <p>The target class is a string and no optional maid API type appears in this mixin, so the
 * class is skipped when TLM is absent.
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")
public abstract class MaidMeleeAttackMixin {
    @Inject(method = "isWithinMeleeAttackRange(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void kusoPenguin$useContactMeleeIntent(LivingEntity target, CallbackInfoReturnable<Boolean> ci) {
        Mob self = (Mob) (Object) this;
        if (MaidKickCompat.isPenguinMaid(self)) {
            // Prevent swing/cooldown from being consumed at the native interaction/favorability
            // distance; the kick chooser (2..4.5 blocks) remains a separate launch decision.
            ci.setReturnValue(MaidKickCompat.hasContact(self, target));
        }
    }

    @Inject(method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void kusoPenguin$blockOrdinaryMaidMelee(Entity target, CallbackInfoReturnable<Boolean> ci) {
        Mob self = (Mob) (Object) this;
        // While the shared kick owns this maid, only its MOB_STRIKE dispatch may reach native TLM
        // damage. Outside that dispatch, a penguin maid still needs real box contact.
        if (PenguinKick.blockOrdinaryMelee(self)
                || MaidKickCompat.isPenguinMaid(self) && !PenguinKick.dispatching(self)
                    && !MaidKickCompat.hasContact(self, target)) {
            ci.setReturnValue(false);
        }
    }
}
