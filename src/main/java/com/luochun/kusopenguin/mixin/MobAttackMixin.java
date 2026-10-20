package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.animation.PenguinActionTriggers;
import com.luochun.kusopenguin.animation.PenguinKick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports successful vanilla mob melee hits without adding AI or changing their result. */
@Mixin(Mob.class)

public abstract class MobAttackMixin {
    @Inject(method="doHurtTarget",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$singleTargetKick(Entity target,CallbackInfoReturnable<Boolean> ci){
        if(PenguinKick.blockOrdinaryMelee((Mob)(Object)this))ci.setReturnValue(false);
    }
    @Inject(method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void kusoPenguin$flyingKick(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            PenguinActionTriggers.mobHit((Mob) (Object) this, target);
        }
    }
}
