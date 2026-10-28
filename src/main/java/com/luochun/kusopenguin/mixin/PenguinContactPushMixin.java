package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.luochun.kusopenguin.animation.PenguinKick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * During an active kick, ordinary entity-contact separation must not look like an external
 * impulse. Only the two native {@code Entity.push(DDD)} calls inside {@code Entity.push(Entity)}
 * are wrapped; direct push(DDD)/push(Vec3), hurt, explosions, knockback and block collision are
 * left untouched. The non-kicking participant of the same contact still receives its push.
 */
@Mixin(Entity.class)
public abstract class PenguinContactPushMixin {
    @WrapOperation(
            method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
            ),
            require = 2
    )
    private static void kusoPenguin$skipContactPush(Entity receiver, double x, double y, double z,
                                                     Operation<Void> original) {
        if (receiver instanceof LivingEntity living && PenguinKick.active(living)) return;
        original.call(receiver, x, y, z);
    }
}
