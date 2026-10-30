package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.vocals.VocalItems;
import immersive_melodies.client.sound.CancelableSoundInstance;
import immersive_melodies.client.sound.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mutes only the IM note sound emitted by an entity holding a microphone.
 *
 * <p>The redirect delegates to the same {@link SoundManager} call, preserving note timing, pitch,
 * length, sustain, delay, progress updates and particles. Volume is zero only when the source
 * living entity holds a microphone in either hand; all other entities and all global game/MIDI
 * audio are untouched. This is not a second music engine and never mutes unrelated players.</p>
 */
@Pseudo
@Mixin(targets = "immersive_melodies.item.InstrumentItem", remap = false)
public abstract class MelodiesMicrophoneSoundMixin {
    private static final String PLAY_NOTE =
        "playNote(Lnet/minecraft/world/entity/Entity;"
            + "Limmersive_melodies/resources/Note;JZ)"
            + "Limmersive_melodies/client/sound/CancelableSoundInstance;";
    private static final String PLAY_SOUND =
        "Limmersive_melodies/client/sound/SoundManager;"
            + "playSound(DDDLnet/minecraft/sounds/SoundEvent;"
            + "Lnet/minecraft/sounds/SoundSource;FFJJJLnet/minecraft/world/entity/Entity;)"
            + "Limmersive_melodies/client/sound/CancelableSoundInstance;";

    @Redirect(
        method = PLAY_NOTE,
        at = @At(value = "INVOKE", target = PLAY_SOUND),
        remap = false,
        require = 1
    )
    private CancelableSoundInstance kuso$muteHeldMicrophone(SoundManager manager, double x,
                                                            double y, double z,
                                                            SoundEvent event,
                                                            SoundSource category, float volume,
                                                            float pitch, long length,
                                                            long sustain, long delay,
                                                            Entity entity) {
        float effectiveVolume = holdsMicrophone(entity) ? 0.0F : volume;
        return manager.playSound(x, y, z, event, category, effectiveVolume, pitch, length,
            sustain, delay, entity);
    }

    private static boolean holdsMicrophone(Entity entity) {
        return entity instanceof LivingEntity living
            && (VocalItems.isMicrophone(living.getMainHandItem())
                || VocalItems.isMicrophone(living.getOffhandItem()));
    }
}
