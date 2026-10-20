package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.animation.PenguinActionTriggers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reports only a confirmed vanilla player hit. Sprint/charge eligibility is captured before the
 * hurt call, which is invoked exactly once and its boolean result is returned unchanged.
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Redirect(
            method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean kusoPenguin$flyingKick(Entity target, DamageSource source, float amount) {
        Player player = (Player) (Object) this;
        boolean wasSprinting = player.isSprinting();
        float attackStrength = player.getAttackStrengthScale(0.5F);
        boolean hit = target.hurt(source, amount);
        if (hit) {
            PenguinActionTriggers.playerHit(player, wasSprinting, attackStrength);
        }
        return hit;
    }
}
