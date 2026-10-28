package com.luochun.kusopenguin.animation;

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

    /**
     * Legacy attack hook with no current caller. It must start a real kick through the same
     * server-owned activation/cooldown path, never a pose-only FLYING_KICK.
     */
    public static void playerHit(Player player, boolean wasSprinting, float attackStrength) {
        if (!wasSprinting || !(attackStrength > 0.9F)) {
            return;
        }
        PenguinKick.tryStart(player);
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
        if(!PenguinKick.independentMob(mob))PenguinKick.tryStart(mob);
    }
}
