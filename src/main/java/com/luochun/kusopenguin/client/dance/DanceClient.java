package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.dance.DanceClientHooks;
import com.luochun.kusopenguin.dance.DanceLibrary;
import com.luochun.kusopenguin.dance.DancePackets;
import com.luochun.kusopenguin.dance.Prefs;
import com.luochun.kusopenguin.dance.Source;
import com.luochun.kusopenguin.dance.Trigger;
import com.luochun.kusopenguin.compat.MelodiesDanceCompat;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Main-thread dance client state and the bridge used by the next render step. Server packets are
 * the only authority for catalog ownership and playback; this class never mutates world entities.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DanceClient implements DanceClientHooks.Listener {
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile DancePackets.Catalog catalog;
    private static final Map<UUID, DancePackets.Playback> PLAYBACK = new HashMap<>();
    private static final java.util.Set<String> REQUESTED = new java.util.HashSet<>();
    /** Server-verified fake targets with the dimension carried by the marker, never client claims. */
    private static final Map<UUID, ResourceLocation> FAKE_TARGETS = new HashMap<>();
    private static ResourceLocation knownDimension;

    public DanceClient() {
    }

    public static void refresh() {
        if (MC.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new DancePackets.Request(DancePackets.Action.LIST,
            UUID.randomUUID(), "", "", 0, ""));
    }

    public static List<DanceLibrary.Builtin> builtins() {
        DancePackets.Catalog current = catalog;
        return current == null ? DanceLibrary.builtins() : current.builtins();
    }

    public static List<DanceLibrary.Clip> clips() {
        DancePackets.Catalog current = catalog;
        return current == null ? List.of() : current.clips();
    }

    public static Prefs prefs() {
        DancePackets.Catalog current = catalog;
        return current == null ? Prefs.defaults() : current.prefs();
    }

    public static DanceLibrary.Clip ownClip(String hash) {
        if (!DanceLibrary.isStrictHash(hash)) {
            return null;
        }
        for (DanceLibrary.Clip clip : clips()) {
            if (clip.hash().equals(hash)) {
                return clip;
            }
        }
        return null;
    }

    public static boolean selectable(String clipId) {
        return DanceLibrary.isBuiltin(clipId) || ownClip(clipId) != null;
    }

    public static CompletableFuture<PenguinAnimationClip> requestClip(String hash) {
        return DanceTransfers.clip(hash);
    }

    public static void setPreference(Trigger trigger, String clipId) {
        if (MC.player == null) {
            return;
        }
        DanceTransfers.queueControl(new DancePackets.Control(DancePackets.Action.SET_PREF,
            UUID.randomUUID(), MC.player.getUUID(), clipId == null ? "" : clipId, trigger, -1));
    }

    public static void setSlot(int slot, String clipId) {
        if (MC.player == null) {
            return;
        }
        DanceTransfers.queueControl(new DancePackets.Control(DancePackets.Action.SET_SLOT,
            UUID.randomUUID(), MC.player.getUUID(), clipId == null ? "" : clipId, null, slot));
    }

    public static void resetDefaults() {
        Prefs defaults = Prefs.defaults();
        for (Trigger trigger : Trigger.values()) {
            setPreference(trigger, switch (trigger) {
                case MUSIC -> defaults.music();
                case REVIVAL -> defaults.revival();
                case MAID_MUSIC -> defaults.maidMusic();
                case ANIMAL_MUSIC -> defaults.animalMusic();
            });
        }
        for (int slot = 0; slot < Prefs.SLOT_COUNT; slot++) {
            setSlot(slot, defaults.slots().get(slot));
        }
    }

    /** User-facing clip name; never a raw hash for a normal label. */
    public static Component clipLabel(String clipId) {
        if (clipId == null || clipId.isEmpty()) {
            return Component.translatable("gui.kuso_penguin.dance.empty_slot");
        }
        DanceLibrary.Builtin builtin = DanceLibrary.builtin(clipId);
        if (builtin != null) {
            return Component.translatable(builtin.translationKey());
        }
        for (DanceLibrary.Clip clip : clips()) {
            if (clip.hash().equals(clipId)) {
                return Component.literal(clip.title());
            }
        }
        return Component.translatable("gui.kuso_penguin.dance.missing_clip");
    }

    /** Crosshair-first exact maid within four blocks, otherwise the local player. */
    public static UUID pointedMaidOrSelf() {
        if (MC.player == null) {
            return null;
        }
        if (MC.hitResult instanceof EntityHitResult hit
            && hit.getEntity() instanceof LivingEntity living
            && MelodiesDanceCompat.isMaid(living)
            && !living.isRemoved()
            && living.isAlive()
            && MC.player.distanceToSqr(living) <= 16.0D
            && MC.player.hasLineOfSight(living)) {
            return living.getUUID();
        }
        return MC.player.getUUID();
    }

    /**
     * True only for a UUID the server explicitly marked in this exact client dimension. This is the
     * sole client-side fake-player identity; it never derives fake status from a remote entity.
     */
    public static boolean isTrackedFakeTarget(UUID entity) {
        if (entity == null || MC.level == null) {
            return false;
        }
        ResourceLocation dimension = FAKE_TARGETS.get(entity);
        return dimension != null && dimension.equals(MC.level.dimension().location());
    }

    /** K fallback after film/corpse precedence: open the quick wheel on the current target. */
    public static void openWheel() {
        if (MC.player == null || MC.level == null
            || !MC.player.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        UUID target = pointedMaidOrSelf();
        if (target == null) {
            return;
        }
        openWheel(target, null);
    }

    /**
     * Opens the existing wheel for an already captured exact target. The optional display name is
     * used for a boss target; {@code null} keeps the ordinary self/maid label logic.
     */
    public static void openWheel(UUID target, Component targetName) {
        if (MC.player == null || MC.level == null || target == null
            || !MC.player.hasEffect(ModEffects.PENGUIN)) {
            return;
        }
        refresh();
        MC.setScreen(new DanceWheelScreen(target, targetName));
    }

    public static void playSlot(int slot, UUID target) {
        Prefs prefs = prefs();
        if (slot < 0 || slot >= Prefs.SLOT_COUNT || target == null) {
            return;
        }
        String clipId = prefs.slots().get(slot);
        if (clipId.isEmpty()) {
            notify(Component.translatable("message.kuso_penguin.dance.slot_empty", slot + 1));
            return;
        }
        if (MC.player == null) {
            return;
        }
        DanceTransfers.queueControl(new DancePackets.Control(DancePackets.Action.PLAY,
            UUID.randomUUID(), target, clipId, null, -1));
    }

    public static void stopDance(UUID target) {
        if (MC.player == null || target == null) {
            return;
        }
        DanceTransfers.queueControl(new DancePackets.Control(DancePackets.Action.STOP,
            UUID.randomUUID(), target, "", null, -1));
    }

    private static void notify(Component message) {
        if (MC.player != null) {
            MC.player.displayClientMessage(message, true);
        }
    }

    public static void deleteClip(String hash) {
        if (MC.getConnection() == null || !DanceLibrary.isStrictHash(hash)) {
            return;
        }
        PacketDistributor.sendToServer(new DancePackets.Request(DancePackets.Action.DELETE,
            UUID.randomUUID(), hash, "", 0, ""));
    }

    public static void renameClip(String hash, String title) {
        if (MC.getConnection() == null || !DanceLibrary.isStrictHash(hash)) {
            return;
        }
        PacketDistributor.sendToServer(new DancePackets.Request(DancePackets.Action.RENAME,
            UUID.randomUUID(), hash, title == null ? "" : title, 0, ""));
    }

    @Override
    public void onCatalog(DancePackets.Catalog packet) {
        catalog = packet;
        if (MC.screen instanceof DanceWheelScreen wheel) {
            wheel.catalogChanged();
        } else if (MC.screen instanceof DanceScreen screen) {
            screen.catalogChanged();
        } else if (packet.open()) {
            MC.setScreen(new DanceScreen());
        }
    }

    @Override
    public void onReply(DancePackets.Reply reply) {
        DanceTransfers.onReply(reply);
        if (MC.screen instanceof DanceScreen screen) {
            screen.statusChanged();
        }
    }

    @Override
    public void onChunk(DancePackets.Chunk chunk) {
        DanceTransfers.onChunk(chunk);
    }

    @Override
    public void onPlayback(DancePackets.Playback packet) {
        if (packet.playing()) {
            PLAYBACK.put(packet.entity(), packet);
            REQUESTED.remove(packet.clipId()); // A new session may retry a previously unavailable file.
            return;
        }
        DancePackets.Playback old = PLAYBACK.get(packet.entity());
        if (old != null && old.session().equals(packet.session())) {
            PLAYBACK.remove(packet.entity());
        }
    }

    @Override
    public void onFakeTargetMarker(DancePackets.FakeTargetMarker packet) {
        if (packet.tracked()) {
            FAKE_TARGETS.put(packet.entity(), packet.dimension());
            return;
        }
        // A late stop for an old dimension must not erase a marker for the dimension we may already
        // be entering (start-tracking can arrive before the client level switches).
        FAKE_TARGETS.computeIfPresent(packet.entity(), (entity, dimension) ->
            dimension.equals(packet.dimension()) ? null : dimension);
    }

    /** Next-step render hook: exact authoritative source match for one entity. */
    public static boolean active(LivingEntity entity, Source source) {
        DancePackets.Playback playback = playback(entity);
        return playback != null && playback.source() == source;
    }

    /** True while any authoritative new-selection playback is active for this entity. */
    public static boolean dancing(LivingEntity entity) {
        return playback(entity) != null;
    }

    /** Authoritative source for this entity, or {@code null} when no playback is active. */
    public static Source source(LivingEntity entity) {
        DancePackets.Playback playback = playback(entity);
        return playback == null ? null : playback.source();
    }

    /** Next-step render hook: clip id for an authoritative active playback. */
    public static String clip(LivingEntity entity) {
        DancePackets.Playback playback = playback(entity);
        return playback == null ? null : playback.clipId();
    }

    /** Next-step render hook: elapsed seconds including the render partial tick. */
    public static float elapsed(LivingEntity entity, float partial) {
        DancePackets.Playback playback = playback(entity);
        if (playback == null || MC.level == null) {
            return -1.0F;
        }
        return Math.max(0.0F,
            (MC.level.getGameTime() - playback.startTick() + partial) / 20.0F);
    }

    private static DancePackets.Playback playback(LivingEntity entity) {
        if (entity == null || MC.level == null || !entity.isAlive()
            || !entity.hasEffect(ModEffects.PENGUIN)) {
            return null;
        }
        DancePackets.Playback playback = PLAYBACK.get(entity.getUUID());
        if (playback == null || !playback.playing()
            || !playback.dimension().equals(entity.level().dimension().location())
            || !playback.entity().equals(entity.getUUID())) {
            return null;
        }
        return playback;
    }

    public static PenguinAnimationClip cachedClip(String hash) {
        return DanceTransfers.cachedClip(hash);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        DanceTransfers.tick();
        DanceQuickKeys.poll();
        if (MC.level == null) {
            PLAYBACK.clear();
            REQUESTED.clear();
            FAKE_TARGETS.clear();
            knownDimension = null;
            return;
        }
        ResourceLocation dimension = MC.level.dimension().location();
        if (!dimension.equals(knownDimension)) {
            ResourceLocation previous = knownDimension;
            knownDimension = dimension;
            // Drop markers that belonged to the dimension being left. A marker for the new
            // dimension may already have arrived, so matching by the previous value keeps it.
            if (previous != null) {
                FAKE_TARGETS.entrySet().removeIf(entry -> previous.equals(entry.getValue()));
            }
        }
        // Keep queued actors in playback state; request them as transfer slots become free.
        // A failed file is reported once per session rather than retried on every render frame.
        for (DancePackets.Playback playback : PLAYBACK.values()) {
            String hash = playback.clipId();
            if (DanceLibrary.isBuiltin(hash) || REQUESTED.contains(hash)
                    || !playback.dimension().equals(MC.level.dimension().location())) continue;
            if (!DanceTransfers.hasDownloadCapacity()) break;
            REQUESTED.add(hash);
            Object connection = MC.getConnection();
            DanceTransfers.clip(hash).whenComplete((clip, error) -> {
                if (error != null) MC.execute(() -> {
                    if (MC.level != null && connection == MC.getConnection()) DanceTransfers.reportError(error);
                });
            });
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void clear() {
        PLAYBACK.clear();
        REQUESTED.clear();
        FAKE_TARGETS.clear();
        knownDimension = null;
        catalog = null;
        DanceTransfers.clear();
    }
}
