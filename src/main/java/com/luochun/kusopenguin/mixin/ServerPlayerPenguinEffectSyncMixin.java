package com.luochun.kusopenguin.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Restores the penguin effect on the dismounting rider's own client. Vanilla
 * {@link ServerPlayer#stopRiding()} sends a removal packet for every active effect of the former
 * LivingEntity vehicle, even though those effects still really exist on the vehicle.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPenguinEffectSyncMixin {
    @WrapMethod(method = "stopRiding")
    private void kusoPenguin$resyncPenguinEffectAfterDismount(Operation<Void> original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Entity formerVehicle = self.getVehicle();
        original.call();
        // WHY after original: vanilla riding HUD cleanup sends its removal packets first and only
        // the observer-only client effect is invalidated; the server effect is still active. The
        // fresh update must follow those removals, while EntityMountEvent would fire too early.
        if (!(formerVehicle instanceof LivingEntity vehicle)
                || vehicle.isRemoved()
                || vehicle.level() != self.level()) {
            return;
        }
        MobEffectInstance penguin = vehicle.getEffect(ModEffects.PENGUIN);
        if (penguin == null) {
            return;
        }
        self.connection.send(new ClientboundUpdateMobEffectPacket(vehicle.getId(), penguin, false));
    }
}
