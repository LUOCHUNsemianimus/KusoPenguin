package com.luochun.kusopenguin;

import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.animation.PenguinPoseSync;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Re-sends the current penguin effect when a player starts tracking an entity that already has it.
 * Pairing data is sent before this event, so the update packet arrives after the entity exists.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectTrackingEvents {
    private EffectTrackingEvents() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        MobEffectInstance effect = target.getEffect(ModEffects.PENGUIN);
        if (effect != null) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
            PenguinPoseSync.snapshot(target,player);
        }
    }
}
