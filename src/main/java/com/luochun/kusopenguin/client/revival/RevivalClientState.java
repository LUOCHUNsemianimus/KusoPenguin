package com.luochun.kusopenguin.client.revival;

import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.revival.RevivalClientHooks;
import com.luochun.kusopenguin.revival.RevivalManager;
import com.luochun.kusopenguin.revival.RevivalPackets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Purely presentational mirror of the server's revival channel. Entries are strictly bounded:
 * the corpse ritual lives for 8 seconds and the held-film ritual for 5 seconds, each plus a
 * one-second transit grace. An old stop packet can never overwrite a newer start.
 */
public final class RevivalClientState {
    private static final long GRACE_MILLIS = 1_000L;

    private record Key(ResourceLocation dimension, UUID caster) {
    }

    private record Entry(int entityId, UUID target, boolean maidTarget, long started,
                         int session, boolean active, long receivedAtMillis) {
    }

    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private static int cleanupTick;

    private RevivalClientState() {
    }

    public static void install() {
        RevivalClientHooks.install(RevivalClientState::receive);
    }

    private static void receive(RevivalPackets.State payload) {
        Key key = new Key(payload.dimension(), payload.caster());
        Entry old = ENTRIES.get(key);
        if (old != null && !isNewer(payload, old)) {
            return;
        }
        ENTRIES.put(key, new Entry(payload.entityId(), payload.target(),
                payload.maidTarget(), payload.startedGameTime(), payload.session(), payload.active(),
                System.currentTimeMillis()));
        RevivalClient.handleState(payload);
    }

    /** A newer start wins; a delayed stop from an older session is ignored. */
    private static boolean isNewer(RevivalPackets.State payload, Entry old) {
        if (payload.startedGameTime() != old.started()) {
            return payload.startedGameTime() > old.started();
        }
        return payload.session() >= old.session();
    }

    public static boolean pole(LivingEntity entity) {
        return entry(entity) != null;
    }

    public static double elapsed(LivingEntity entity, float partial) {
        Entry entry = raw(entity);
        if (entry == null) {
            return 0.0D;
        }
        return Math.max(0.0D,
                (entity.level().getGameTime() - entry.started() + partial) / 20.0D);
    }

    public static UUID target(LivingEntity entity) {
        Entry entry = entry(entity);
        return entry == null ? null : entry.target();
    }

    /** Corpse-token target only; a film hold token must never be sent back as a corpse request. */
    public static UUID corpseTarget(LivingEntity entity) {
        Entry entry = entry(entity);
        return entry == null || entry.maidTarget() ? null : entry.target();
    }

    private static Entry entry(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        Entry entry = raw(entity);
        if (entry == null || !entry.active()) {
            return null;
        }
        if (!entity.isAlive() || !entity.hasEffect(ModEffects.PENGUIN)) {
            return null;
        }
        if (expired(entry)) {
            ENTRIES.remove(key(entity), entry);
            return null;
        }
        return entry;
    }

    private static Entry raw(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        Entry entry = ENTRIES.get(key(entity));
        return entry != null && entry.entityId() == entity.getId() ? entry : null;
    }

    private static Key key(LivingEntity entity) {
        return new Key(entity.level().dimension().location(), entity.getUUID());
    }

    private static boolean expired(Entry entry) {
        return System.currentTimeMillis() - entry.receivedAtMillis() > expiryMillis(entry);
    }

    /** The State packet keeps the maid flag: 100 ticks for film, 160 ticks for the player path. */
    private static long expiryMillis(Entry entry) {
        long ticks = entry.maidTarget() ? RevivalManager.FILM_DURATION_TICKS
                : RevivalManager.DURATION_TICKS;
        return ticks * 50L + GRACE_MILLIS;
    }

    public static void cleanup() {
        if (++cleanupTick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        ENTRIES.entrySet().removeIf(entry -> !entry.getValue().active()
                || now - entry.getValue().receivedAtMillis() > expiryMillis(entry.getValue()));
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
