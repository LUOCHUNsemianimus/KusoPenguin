package com.luochun.kusopenguin.registry;

import com.google.common.collect.MapMaker;
import com.luochun.kusopenguin.KusoPenguin;
import java.util.Collections;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared accepted-action sounds. Registration follows the custom disc's sound event. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, KusoPenguin.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FLYING_KICK =
        register("flying_kick");
    public static final DeferredHolder<SoundEvent, SoundEvent> PENGUIN_DEATH =
        register("penguin_death");

    // Weak identity keys keep one death sound per entity life. Both PenguinDeathMixin targets
    // (LivingEntity and ServerPlayer) can reach die for the same instance.
    private static final Set<LivingEntity> SOUNDED_DEATHS =
        Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return SOUND_EVENTS.register(id, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, id)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    /** Called once by the accepted kick, after the server stored its Flight. */
    public static void playKick(LivingEntity entity) {
        play(entity, FLYING_KICK.get());
    }

    /**
     * Called by the accepted-death tail for any LivingEntity, including visual-only bosses.
     * The PENGUIN check and the per-life set are deliberately independent of pose eligibility.
     */
    public static void playDeath(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || entity.isSilent()
            || !entity.isDeadOrDying() || !entity.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        if (!SOUNDED_DEATHS.add(entity)) {
            return;
        }
        play(entity, PENGUIN_DEATH.get());
    }

    private static void play(LivingEntity entity, SoundEvent sound) {
        if (!(entity.level() instanceof ServerLevel level) || entity.isSilent()
            || !entity.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        float volume = entity instanceof Player ? 1.0F : .45F;
        SoundSource source = entity.getSoundSource();
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, source, volume, 1.0F);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
            SOUNDED_DEATHS.remove(entity);
        }
    }

    /** A revived entity is alive again, so a later death in the same instance may sound again. */
    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && entity.level() instanceof ServerLevel
            && entity.isAlive() && !entity.isDeadOrDying()) {
            SOUNDED_DEATHS.remove(entity);
        }
    }
}
