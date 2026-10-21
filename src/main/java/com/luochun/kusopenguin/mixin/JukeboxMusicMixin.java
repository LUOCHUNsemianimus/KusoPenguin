package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.music.MusicSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes native playback without replacing insertion, persistence, comparator or ejection. */
@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxMusicMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void kusoPenguin$musicTick(Level level, BlockPos pos, BlockState state,
                                            JukeboxBlockEntity jukebox, CallbackInfo ci) {
        MusicSessions.observe(jukebox);
    }
    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void kusoPenguin$musicChange(ItemStack item, CallbackInfo ci) {
        MusicSessions.observe((JukeboxBlockEntity) (Object) this);
    }
}
