package com.luochun.kusopenguin.mixin;
import com.luochun.kusopenguin.animation.PenguinKick;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class PenguinTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$kickTravel(Vec3 input,CallbackInfo ci){if(PenguinKick.travel((LivingEntity)(Object)this))ci.cancel();}
}
