package com.luochun.kusopenguin.mixin;

import com.luochun.kusopenguin.revival.RevivalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * One-shot override used only while the revival service is completing. HEAD runs before the
 * native respawn-anchor charge logic; the resulting transition still goes through the normal
 * PlayerRespawnPositionEvent and PlayerList.respawn path.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRevivalMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void kusoPenguin$pendingRevivalRespawn(boolean keepInventory,
                                                   DimensionTransition.PostDimensionTransition post,
                                                   CallbackInfoReturnable<DimensionTransition> cir) {
        DimensionTransition transition = RevivalManager.consumePendingRespawn(
                (ServerPlayer) (Object) this);
        if (transition != null) {
            cir.setReturnValue(transition);
        }
    }
}
