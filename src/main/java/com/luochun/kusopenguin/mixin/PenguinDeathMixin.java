package com.luochun.kusopenguin.mixin;
import com.luochun.kusopenguin.animation.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** TAIL is reached after accepted death, not the cancellable-death early return. */
@Mixin({LivingEntity.class,ServerPlayer.class})
public abstract class PenguinDeathMixin {
    @Inject(method="die",at=@At("TAIL"))
    private void kusoPenguin$visualDeath(DamageSource source,CallbackInfo ci){
        LivingEntity e=(LivingEntity)(Object)this;
        if(e.isDeadOrDying()&&!e.isBaby()&&PenguinEntitySupport.isSupportedType(e.getType()))
            PenguinPoseSync.start(e,PenguinAction.DEATH);
    }
}
