package com.luochun.kusopenguin.client;

import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.client.compat.MaidCompatClient;
import com.luochun.kusopenguin.registry.ModEffects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;

/**
 * One shared gate for every draw/filter path. Unsupported states deliberately keep the complete
 * vanilla renderer instead of mixing the original head with a penguin body.
 *
 * <p>Adult players keep the penguin body while crouching, riding, swimming or fall-flying
 * (Elytra) because {@link PenguinPlayerPosture} fits those native poses. Other mobs that were
 * excluded in those states keep their previous complete vanilla renderer.
 *
 * <p>The seven prone animals use the same gate for baby/invisible/glowing/passenger/swim
 * fallbacks, but they keep the replacement while crouching because the prone pose is static and
 * independent of leg animation (this is what keeps an ordinary stalking cat replaced). Native
 * sitting and cat lie-down still hand the whole entity back to vanilla.
 */
public final class PenguinRenderSupport {
    private PenguinRenderSupport() {
    }

    /** Client render gate: upright action-capable types plus the explicit prone render set. */
    public static boolean isSupportedType(EntityType<?> type) {
        return PenguinEntitySupport.isSupportedType(type)
                || PenguinEntitySupport.isProneType(type);
    }

    /**
     * Separate visual-only eligibility for the seven ordinary Iron humanoids. It intentionally does
     * not feed the action/AI/mana/cooldown sets, and the optional bridge still requires an active
     * effect, enabled skinned mesh and a complete native bone preflight.
     */
    public static boolean isIronHumanoidType(EntityType<?> type) {
        return com.luochun.kusopenguin.client.compat.IronHumanoidCompatClient.isWhitelisted(type);
    }

    public static boolean isProneType(EntityType<?> type) {
        return PenguinEntitySupport.isProneType(type);
    }

    /** True only while this entity is currently drawn as a lowered, sliding penguin body. */
    public static boolean isProne(LivingEntity entity) {
        return isProneType(entity.getType()) && shouldReplace(entity)
                && !PenguinRenderContext.isDanceFrame(entity);
    }

    public static boolean shouldReplace(LivingEntity entity) {
        if(PenguinDeathClient.rendering(entity))return true;
        if (!isSupportedType(entity.getType()) && !isIronHumanoidType(entity.getType())) {
            return false;
        }        if (entity.isBaby()
                || entity.isInvisible()
                || entity.isCurrentlyGlowing()
                || entity.isSpectator()) {
            return false;
        }
        boolean prone = isProneType(entity.getType());
        // Native fall-flying is fitted for adult players only. Other mobs keep the previous
        // complete-vanilla fall-flying renderer/exclusion.
        if (entity.isFallFlying() && !(entity instanceof Player)) {
            return false;
        }
        if (!(entity instanceof Player)) {
            if (entity.isPassenger() || entity.isSwimming() || entity.hasPose(Pose.SITTING)) {
                return false;
            }
            // Sitting wolves/cats and a cat's native lie-down rotate the whole renderer root and
            // would fight the static prone transform, so those states keep the vanilla renderer.
            if (entity instanceof TamableAnimal tame && tame.isInSittingPose()) {
                return false;
            }
            if (entity instanceof Cat cat && cat.isLying()) {
                return false;
            }
            // A prone body is already low and its limbs never animate. Crouching (cat stalking)
            // therefore does not need the upright "complete vanilla" fallback.
            if (!prone && entity.isCrouching()) {
                return false;
            }
        }
        return entity.hasEffect(ModEffects.PENGUIN);
    }

    /**
     * Model-aware maid gate used before suppressing/re-anchoring native maid layers. Unlike the
     * entity-only gate this also requires the Bedrock standard-bone contract, so a missing head or
     * limb keeps the complete native maid instead of a penguin body without a native head.
     */
    public static boolean isMaidReplacement(LivingEntity entity, EntityModel<?> model) {
        return MaidCompatClient.isMaidModel(model)
                && MaidCompatClient.isReady(model)
                && shouldReplace(entity);
    }

    /**
     * Draws the native Elytra layer on the penguin back only for a replaced player that is
     * actually fall-flying. A detached death copy keeps its existing hidden-Elytra policy even if
     * its copied state is fall-flying; every other state also keeps the previous hidden policy.
     */
    public static boolean shouldMountElytraOnBack(LivingEntity entity) {
        return entity instanceof Player
                && entity.isFallFlying()
                && !PenguinDeathClient.rendering(entity)
                && shouldReplace(entity);
    }
}
