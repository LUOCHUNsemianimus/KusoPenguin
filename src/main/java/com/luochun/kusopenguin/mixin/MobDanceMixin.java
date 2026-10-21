package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.music.DanceManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary ownership, not a persisted NoAI flag; native AI resumes as soon as membership ends. */
@Mixin(Mob.class)
public abstract class MobDanceMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void kusoPenguin$pauseAI(CallbackInfo ci) {
        if (DanceManager.pausesAI((Mob) (Object) this)) ci.cancel();
    }
}
