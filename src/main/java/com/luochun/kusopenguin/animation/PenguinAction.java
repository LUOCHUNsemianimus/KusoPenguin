package com.luochun.kusopenguin.animation;

import com.luochun.kusopenguin.KusoPenguin;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-owned cosmetic actions. The same clip id is used by the resource reloader and by the
 * client cache, so no action-specific packet or map exists.
 */
public enum PenguinAction {
    TAUNT("taunt", 0, 3.433333F),
    FLYING_KICK("flying_kick", 1, 1.8F);

    private final ResourceLocation animationId;
    private final int priority;
    private final float durationSeconds;

    PenguinAction(String id, int priority, float durationSeconds) {
        this.animationId = ResourceLocation.fromNamespaceAndPath(
                KusoPenguin.MODID, "animations/" + id + ".json");
        this.priority = priority;
        this.durationSeconds = durationSeconds;
    }

    public ResourceLocation animationId() {
        return animationId;
    }

    public int priority() {
        return priority;
    }

    public float durationSeconds() {
        return durationSeconds;
    }

    public long durationTicks() {
        return Math.round(durationSeconds * 20.0F);
    }
}
