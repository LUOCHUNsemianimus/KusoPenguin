package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.animation.PenguinPoseSync;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla only sends effect packets to passengers, so observers never learn about effects added,
 * updated or removed on a tracked entity. Mirror only the penguin effect with the vanilla packets.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectSyncMixin {
    @Inject(
            method = "onEffectAdded(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("TAIL")
    )
    private void kusoPenguin$syncAddedEffect(MobEffectInstance effectInstance, Entity source,
                                             CallbackInfo callback) {
        this.kusoPenguin$broadcastEffect(effectInstance, false);
        if(effectInstance.getEffect().value()==ModEffects.PENGUIN.value())PenguinPoseSync.started((LivingEntity)(Object)this);
    }

    @Inject(
            method = "onEffectUpdated(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V",
            at = @At("TAIL")
    )
    private void kusoPenguin$syncUpdatedEffect(MobEffectInstance effectInstance, boolean forced,
                                               Entity source, CallbackInfo callback) {
        this.kusoPenguin$broadcastEffect(effectInstance, false);
    }

    @Inject(
            method = "onEffectRemoved(Lnet/minecraft/world/effect/MobEffectInstance;)V",
            at = @At("TAIL")
    )
    private void kusoPenguin$syncRemovedEffect(MobEffectInstance effectInstance, CallbackInfo callback) {
        this.kusoPenguin$broadcastEffect(effectInstance, true);
        if(effectInstance.getEffect().value()==ModEffects.PENGUIN.value())PenguinPoseSync.removed((LivingEntity)(Object)this);
    }

    private void kusoPenguin$broadcastEffect(MobEffectInstance effectInstance, boolean removed) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (effectInstance.getEffect().value() != ModEffects.PENGUIN.value()) {
            return;
        }
        Packet<?> packet = removed
                ? new ClientboundRemoveMobEffectPacket(self.getId(), effectInstance.getEffect())
                : new ClientboundUpdateMobEffectPacket(self.getId(), effectInstance, false);
        serverLevel.getChunkSource().broadcastAndSend(self, packet);
    }
}
