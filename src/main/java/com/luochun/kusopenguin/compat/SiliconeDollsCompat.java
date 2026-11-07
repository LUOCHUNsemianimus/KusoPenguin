package com.luochun.kusopenguin.compat;

import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Optional, common-safe SiliconeDolls target check for the dance actor manager.
 *
 * <p>SiliconeDolls fake players are real {@code ServerPlayer} instances on the server, so they must
 * never be accepted through a generic {@link net.minecraft.world.entity.player.Player} branch.
 * Only the exact class {@code dev.anvilcraft.rg.sd.entity.FakePlayer} whose {@code isShadow()}
 * accessor returns false is a dance target. All third-party types stay in reflection; a missing
 * mod, class, accessor or failed invocation fails closed. The client never learns fake identity
 * from this helper; it only trusts a server-sent marker.</p>
 */
public final class SiliconeDollsCompat {
    private static final String MOD_ID = "silicone_dolls";
    private static final String FAKE_PLAYER_CLASS = "dev.anvilcraft.rg.sd.entity.FakePlayer";
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private static volatile Method isShadow;
    private static volatile boolean resolved;

    private SiliconeDollsCompat() {
    }

    /**
     * True only for an exact non-shadow SiliconeDolls fake player. Ordinary players, other fake
     * player implementations, shadow copies and reflection failures all return false.
     */
    public static boolean isDanceTarget(LivingEntity entity) {
        if (!AVAILABLE || entity == null || !FAKE_PLAYER_CLASS.equals(entity.getClass().getName())) {
            return false;
        }
        Method accessor = isShadowMethod();
        if (accessor == null) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(accessor.invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method isShadowMethod() {
        if (!resolved) {
            synchronized (SiliconeDollsCompat.class) {
                if (!resolved) {
                    try {
                        isShadow = Class.forName(FAKE_PLAYER_CLASS).getMethod("isShadow");
                    } catch (ReflectiveOperationException | LinkageError e) {
                        isShadow = null;
                    }
                    resolved = true;
                }
            }
        }
        return isShadow;
    }
}
