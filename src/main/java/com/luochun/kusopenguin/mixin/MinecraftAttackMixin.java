package com.luochun.kusopenguin.mixin;
import com.luochun.kusopenguin.client.PenguinKickInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$activeKick(CallbackInfoReturnable<Boolean> ci){if(PenguinKickInput.attack())ci.setReturnValue(false);}
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void kusoPenguin$noMiningDuringKick(boolean held,CallbackInfo ci){if(PenguinKickInput.moving())ci.cancel();}
}
