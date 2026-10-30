package com.luochun.kusopenguin.compat;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/**
 * Optional TLM melee-kick gate. The concrete maid API is isolated in the lazy nested class so a
 * standalone game or dedicated server without TLM never loads a maid type. This is only a task
 * gate, not broad permission to attack friendly mobs.
 */
public final class MaidKickCompat {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private MaidKickCompat() {
    }

    /**
     * Eligible only for an actual EntityMaid in the exact attack task with a live current target.
     * Ground contact is intentionally absent: the shared tryStart already requires it, while the
     * flight checks call this again to detect a task/target/sit change.
     */
    public static boolean isEligibleMaid(LivingEntity entity) {
        return AVAILABLE && PenguinEntitySupport.isMaid(entity.getType()) && Impl.isEligible(entity);
    }

    /**
     * True only for the actual optional maid type while the penguin effect is active. This gate is
     * deliberately separate from {@link #isEligibleMaid}: any ordinary penguin-maid melee must be
     * physical, including attacks outside the shared kick task.
     */
    public static boolean isPenguinMaid(LivingEntity entity) {
        return AVAILABLE && PenguinEntitySupport.isMaid(entity.getType())
                && entity.hasEffect(ModEffects.PENGUIN);
    }

    /**
     * Shared ordinary-melee contact test for the native AI range override and the doHurtTarget
     * pre-gate. The kick's own dispatch is not ordinary melee: it is owned by the bounded per-step
     * flight sweep and identified by {@code PenguinKick.dispatching(Mob)} instead.
     */
    public static boolean hasContact(Mob maid, Entity target) {
        return maid.getBoundingBox().inflate(.35, .15, .35).intersects(target.getBoundingBox());
    }

    private static final class Impl {
        private static final ResourceLocation MELEE_TASK_UID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "attack");

        private Impl() {
        }

        static boolean isEligible(LivingEntity entity) {
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) {
                return false;
            }
            if (!maid.isAlive() || maid.isRemoved() || maid.isInSittingPose() || maid.isPassenger()) {
                return false;
            }
            var task = maid.getTask();
            if (task == null || !MELEE_TASK_UID.equals(task.getUid())) {
                return false;
            }
            // Ranged/farm/idle tasks fail the UID gate above. The native melee task also excludes
            // players, owned pets and configured allies; isAlliedTo is checked both directions.
            LivingEntity target = maid.getTarget();
            if (target == null || !target.isAlive() || target.isRemoved()
                || maid.isAlliedTo(target) || target.isAlliedTo(maid)) {
                return false;
            }
            return maid.canAttack(target);
        }
    }
}
