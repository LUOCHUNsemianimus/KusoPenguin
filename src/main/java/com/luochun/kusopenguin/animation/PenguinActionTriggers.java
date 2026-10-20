package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Shared server-side eligibility checks for attack-driven actions. Callers report only a confirmed
 * vanilla hit; these checks never cancel, extend or redirect the original attack.
 */
public final class PenguinActionTriggers {
    private PenguinActionTriggers() {
    }

    public static void playerHit(Player player, boolean wasSprinting, float attackStrength) {
        if (!wasSprinting || !(attackStrength > 0.9F)) {
            return;
        }
        trigger(player, PenguinAction.FLYING_KICK);
    }

    public static void mobHit(Mob mob, Entity target) {
        if (!(mob instanceof Enemy) && !(mob instanceof Monster)) {
            return;
        }
        if (target == mob || !(target instanceof LivingEntity)) {
            return;
        }
        if (!PenguinEntitySupport.isSupportedType(mob.getType())) {
            return;
        }
        trigger(mob, PenguinAction.FLYING_KICK);
    }

    private static void trigger(LivingEntity entity, PenguinAction action) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        if (!entity.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        PenguinPoseSync.start(entity, action);
    }
}
