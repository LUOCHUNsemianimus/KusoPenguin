package com.luochun.kusopenguin.mixin;
import com.luochun.kusopenguin.animation.PenguinKick;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Inject(method="getAttackStrengthScale",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$normalWeaponDamage(float partial,CallbackInfoReturnable<Float> ci){
        if(PenguinKick.dispatching((Player)(Object)this))ci.setReturnValue(1F);
    }
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$oneAttackPath(Entity target,CallbackInfo ci){
        Player p=(Player)(Object)this;if(PenguinKick.active(p)&&!PenguinKick.dispatching(p))ci.cancel();
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean kusoPenguin$confirmedHit(Entity target,DamageSource source,float amount){
        boolean hit=target.hurt(source,PenguinKick.damage((Player)(Object)this,amount));PenguinKick.confirmedHit((Player)(Object)this,hit);return hit;
    }
}
