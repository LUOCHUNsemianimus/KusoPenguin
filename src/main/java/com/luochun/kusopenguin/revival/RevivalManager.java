package com.luochun.kusopenguin.revival;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinKick;
import com.luochun.kusopenguin.compat.MaidRevivalCompat;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative revival service. All channels, reservations, corpse markers and pending
 * respawn transitions live in a map keyed by the running server, never in cross-world statics.
 *
 * <p>A player reservation is keyed by the exact corpse-marker entity UUID (the death-generation
 * token); a maid reservation is keyed by the exact tombstone UUID. Channels stay keyed by caster
 * UUID so the caster can always cancel/replace its own channel.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RevivalManager {
    public static final int DURATION_TICKS = 160;
    private static final double RANGE = 4.0D;
    private static final double LOOK_DOT = 0.82D;
    private static final double MOVE_LIMIT = 0.75D;
    private static final int PROGRESS_INTERVAL = 20;
    private static final int RECREATE_RETRY_TICKS = 20;
    private static final Map<MinecraftServer, ServerState> STATES = new HashMap<>();

    private enum TargetKind {
        PLAYER, MAID
    }

    /** A dead player's marker record; the token survives marker chunk-unload recreation. */
    private static final class CorpseRecord {
        final ServerPlayer player;
        final UUID token;
        final ResourceKey<Level> dimension;
        final Vec3 position;
        final float yaw;
        final long deathTick;
        final String name;
        final boolean penguin;
        RevivalCorpse corpse;
        long nextRecreateTick;

        CorpseRecord(ServerPlayer player, UUID token, ResourceKey<Level> dimension, Vec3 position,
                     float yaw, long deathTick, String name, boolean penguin, RevivalCorpse corpse) {
            this.player = player;
            this.token = token;
            this.dimension = dimension;
            this.position = position;
            this.yaw = yaw;
            this.deathTick = deathTick;
            this.name = name;
            this.penguin = penguin;
            this.corpse = corpse;
        }
    }

    private static final class Channel {
        final ServerLevel level;
        final ServerPlayer caster;
        final UUID targetKey;
        final TargetKind kind;
        final ServerPlayer targetPlayer;
        final MaidRevivalCompat.Target maidTarget;
        final Vec3 startPos;
        final long startTick;
        final int session;

        Channel(ServerLevel level, ServerPlayer caster, UUID targetKey, TargetKind kind,
                ServerPlayer targetPlayer, MaidRevivalCompat.Target maidTarget, Vec3 startPos,
                long startTick, int session) {
            this.level = level;
            this.caster = caster;
            this.targetKey = targetKey;
            this.kind = kind;
            this.targetPlayer = targetPlayer;
            this.maidTarget = maidTarget;
            this.startPos = startPos;
            this.startTick = startTick;
            this.session = session;
        }
    }

    private static final class ServerState {
        final Map<UUID, CorpseRecord> corpses = new HashMap<>();
        final Map<UUID, Channel> channels = new HashMap<>();
        final Map<UUID, Channel> reservations = new HashMap<>();
        final Map<UUID, DimensionTransition> pendingRespawns = new HashMap<>();
        int nextSession = 1;
    }

    private RevivalManager() {
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static ServerState stateOrNull(MinecraftServer server) {
        return server == null ? null : STATES.get(server);
    }

    /**
     * LOWEST priority: only an uncancelled death creates a marker, so a later-cancelled death
     * cannot leave a stale corpse behind.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.getServer() == null) {
            return;
        }
        ServerState state = state(player.getServer());
        Channel casterChannel = state.channels.get(player.getUUID());
        if (casterChannel != null) {
            cancel(state, casterChannel,
                    Component.translatable("message.kuso_penguin.revival.cancelled"), true);
        }
        cancelTargeting(state, player.getUUID(), false);
        removeCorpse(state, player.getUUID());
        ServerLevel level = player.serverLevel();
        RevivalCorpse corpse = new RevivalCorpse(RevivalEntities.CORPSE.get(), level);
        corpse.setPos(player.getX(), player.getY(), player.getZ());
        corpse.setYRot(player.getYRot());
        long now = level.getGameTime();
        corpse.configure(player.getUUID(), player.getGameProfile().getName(),
                player.hasEffect(ModEffects.PENGUIN), now);
        if (!level.addFreshEntity(corpse)) {
            return;
        }
        state.corpses.put(player.getUUID(), new CorpseRecord(player, corpse.getUUID(),
                level.dimension(), corpse.position(), corpse.getYRot(), now,
                corpse.ownerName(), corpse.penguin(), corpse));
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            cleanupPlayer(state(player.getServer()), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            cleanupPlayer(state(player.getServer()), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void damage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || !(event.getEntity() instanceof ServerPlayer player)
                || player.getServer() == null) {
            return;
        }
        ServerState state = stateOrNull(player.getServer());
        if (state == null) {
            return;
        }
        Channel channel = state.channels.get(player.getUUID());
        if (channel != null) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.cancelled"), true);
        }
    }

    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer caster) || caster.getServer() == null) {
            return;
        }
        ServerState state = stateOrNull(caster.getServer());
        if (state == null || !(event.getEntity() instanceof ServerPlayer tracker)) {
            return;
        }
        Channel channel = state.channels.get(caster.getUUID());
        if (channel != null) {
            PacketDistributor.sendToPlayer(tracker, packet(channel, true));
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerState state = stateOrNull(event.getServer());
        if (state == null) {
            return;
        }
        pruneCorpses(event.getServer(), state);
        for (Channel channel : new ArrayList<>(state.channels.values())) {
            if (state.channels.get(channel.caster.getUUID()) != channel) {
                continue;
            }
            long elapsed = channel.level.getGameTime() - channel.startTick;
            if (elapsed < 0 || !valid(channel)) {
                cancel(state, channel, Component.translatable("message.kuso_penguin.revival.cancelled"), true);
                continue;
            }
            if (elapsed % PROGRESS_INTERVAL == 0) {
                int seconds = (int) Math.max(0, (DURATION_TICKS - elapsed + 19) / 20);
                Component progress = Component.translatable("message.kuso_penguin.revival.progress", seconds);
                channel.caster.displayClientMessage(progress, true);
                if (channel.kind == TargetKind.PLAYER && channel.targetPlayer.connection != null) {
                    channel.targetPlayer.displayClientMessage(progress, true);
                }
            }
            if (elapsed >= DURATION_TICKS) {
                complete(state, channel);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    /** Handles an exact death-token/tombstone request from the client; never assigns by owner. */
    public static void request(ServerPlayer caster, UUID target) {
        MinecraftServer server = caster.getServer();
        if (server == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = state(server);
        Channel active = state.channels.get(caster.getUUID());
        if (active != null) {
            cancel(state, active, Component.translatable("message.kuso_penguin.revival.cancelled"), true);
            return;
        }
        if (server.isHardcore()) {
            fail(caster, "message.kuso_penguin.revival.hardcore");
            return;
        }
        if (!caster.isAlive() || caster.isSpectator() || !caster.hasEffect(ModEffects.PENGUIN)) {
            fail(caster, "message.kuso_penguin.revival.effect_required");
            return;
        }
        if (target == null || target.equals(caster.getUUID())) {
            fail(caster, "message.kuso_penguin.revival.invalid_target");
            return;
        }
        if (state.reservations.containsKey(target)) {
            fail(caster, "message.kuso_penguin.revival.reserved");
            return;
        }

        CorpseRecord record = findCorpse(state, target);
        if (record != null) {
            ServerPlayer targetPlayer = record.player;
            if (!isCurrent(server, targetPlayer) || targetPlayer == caster
                    || !targetPlayer.isDeadOrDying()
                    || targetPlayer.serverLevel() != level
                    || !record.dimension.equals(level.dimension())
                    || record.corpse == null || record.corpse.isRemoved()
                    || !record.token.equals(record.corpse.getUUID())
                    || !lookingAt(caster, record.corpse)) {
                fail(caster, "message.kuso_penguin.revival.invalid_target");
                return;
            }
            start(state, new Channel(level, caster, target, TargetKind.PLAYER, targetPlayer, null,
                    caster.position(), level.getGameTime(), state.nextSession++));
            return;
        }

        MaidRevivalCompat.Target maid = MaidRevivalCompat.findTarget(level, caster, target);
        if (maid == null) {
            fail(caster, "message.kuso_penguin.revival.invalid_target");
            return;
        }
        Entity tombstone = (Entity) maid.tombstone();
        if (!lookingAt(caster, tombstone)) {
            fail(caster, "message.kuso_penguin.revival.invalid_target");
            return;
        }
        start(state, new Channel(level, caster, target, TargetKind.MAID, null, maid,
                caster.position(), level.getGameTime(), state.nextSession++));
    }

    private static void start(ServerState state, Channel channel) {
        state.channels.put(channel.caster.getUUID(), channel);
        state.reservations.put(channel.targetKey, channel);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster, packet(channel, true));
        channel.caster.displayClientMessage(
                Component.translatable("message.kuso_penguin.revival.started"), true);
    }

    private static boolean valid(Channel channel) {
        ServerPlayer caster = channel.caster;
        if (caster.isRemoved() || !caster.isAlive() || caster.connection == null
                || caster.serverLevel() != channel.level
                || !caster.hasEffect(ModEffects.PENGUIN)
                || caster.isPassenger() || caster.isSwimming() || caster.isFallFlying()
                || PenguinKick.active(caster)
                || caster.position().distanceToSqr(channel.startPos) > MOVE_LIMIT * MOVE_LIMIT) {
            return false;
        }
        if (channel.kind == TargetKind.PLAYER) {
            ServerPlayer target = channel.targetPlayer;
            ServerState state = stateOrNull(caster.getServer());
            CorpseRecord record = state == null ? null : state.corpses.get(target.getUUID());
            return target.isDeadOrDying() && target.serverLevel() == channel.level
                    && record != null && record.player == target
                    && record.token.equals(channel.targetKey)
                    && record.dimension.equals(channel.level.dimension())
                    && record.corpse != null && !record.corpse.isRemoved()
                    && record.token.equals(record.corpse.getUUID())
                    && isCurrent(caster.getServer(), target)
                    && caster.position().distanceToSqr(target.position()) <= RANGE * RANGE;
        }
        return MaidRevivalCompat.validate(channel.level, caster, channel.maidTarget)
                && caster.position().distanceToSqr(channel.maidTarget.position()) <= RANGE * RANGE;
    }

    private static void complete(ServerState state, Channel channel) {
        if (channel.kind == TargetKind.PLAYER) {
            completePlayer(state, channel);
        } else {
            completeMaid(state, channel);
        }
    }

    /**
     * Revives the actual dead target (never the living caster). The saved corpse position/yaw is
     * the one-shot native respawn transition, keepInventory stays false and the caster's own
     * instance/inventory/health/home is never touched.
     */
    private static void completePlayer(ServerState state, Channel channel) {
        ServerPlayer target = channel.targetPlayer;
        CorpseRecord record = state.corpses.get(target.getUUID());
        if (record == null || !record.token.equals(channel.targetKey)
                || record.corpse == null || record.corpse.isRemoved()) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.invalid_target"), true);
            return;
        }
        DimensionTransition transition = safeTransition(channel.level, record.position, record.yaw);
        if (transition == null) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.no_safe_spot"), true);
            return;
        }
        if (target.connection == null || !target.isDeadOrDying()
                || target.serverLevel() != channel.level) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.cancelled"), false);
            return;
        }
        state.channels.remove(channel.caster.getUUID(), channel);
        state.reservations.remove(channel.targetKey, channel);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster, packet(channel, false));
        state.pendingRespawns.put(target.getUUID(), transition);
        ServerPlayer revived;
        try {
            // keepInventory=false preserves native death/XP rules; the mixin returns this one-shot
            // transition before any respawn-anchor charge, so bed/home data stays intact.
            revived = target.getServer().getPlayerList().respawn(
                    target, false, Entity.RemovalReason.KILLED);
        } finally {
            state.pendingRespawns.remove(target.getUUID());
        }
        if (revived == null) {
            throw new IllegalStateException("Native respawn returned no player for " + target.getUUID());
        }
        target.connection.player = revived;
        removeCorpse(state, target.getUUID());
        revived.setHealth(revived.getMaxHealth());
        revived.addEffect(new MobEffectInstance(ModEffects.PENGUIN, 20 * 180, 0));
        revived.displayClientMessage(Component.translatable("message.kuso_penguin.revival.success"), true);
        if (channel.caster.connection != null && channel.caster.isAlive()) {
            channel.caster.displayClientMessage(
                    Component.translatable("message.kuso_penguin.revival.success"), true);
        }
    }

    private static void completeMaid(ServerState state, Channel channel) {
        Vec3 safe = findSafePosition(channel.level, channel.maidTarget.position());
        if (safe == null) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.no_safe_spot"), true);
            return;
        }
        if (!MaidRevivalCompat.resurrect(channel.level, channel.caster, channel.maidTarget, safe)) {
            cancel(state, channel, Component.translatable("message.kuso_penguin.revival.maid_failed"), true);
            return;
        }
        state.channels.remove(channel.caster.getUUID(), channel);
        state.reservations.remove(channel.targetKey, channel);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster, packet(channel, false));
        channel.caster.displayClientMessage(
                Component.translatable("message.kuso_penguin.revival.success"), true);
    }

    private static void cancel(ServerState state, Channel channel, Component message, boolean notify) {
        if (!state.channels.remove(channel.caster.getUUID(), channel)) {
            return;
        }
        state.reservations.remove(channel.targetKey, channel);
        if (channel.caster.connection != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster, packet(channel, false));
        }
        if (notify && channel.caster.connection != null) {
            channel.caster.displayClientMessage(message, true);
        }
    }

    private static void cleanupPlayer(ServerState state, UUID playerId) {
        removeCorpse(state, playerId);
        Channel caster = state.channels.get(playerId);
        if (caster != null) {
            cancel(state, caster, Component.translatable("message.kuso_penguin.revival.cancelled"), false);
        }
        // Reservations are keyed by the death token, so find channels by target owner instead.
        cancelTargeting(state, playerId, false);
    }

    private static void cancelTargeting(ServerState state, UUID playerId, boolean notify) {
        for (Channel channel : new ArrayList<>(state.channels.values())) {
            if (channel.kind == TargetKind.PLAYER && channel.targetPlayer.getUUID().equals(playerId)) {
                cancel(state, channel,
                        Component.translatable("message.kuso_penguin.revival.cancelled"), notify);
            }
        }
    }

    private static void removeCorpse(ServerState state, UUID playerId) {
        CorpseRecord record = state.corpses.remove(playerId);
        if (record != null && record.corpse != null && !record.corpse.isRemoved()) {
            record.corpse.discard();
        }
    }

    private static CorpseRecord findCorpse(ServerState state, UUID token) {
        for (CorpseRecord record : state.corpses.values()) {
            if (record.token.equals(token)) {
                return record;
            }
        }
        return null;
    }

    private static boolean isCurrent(MinecraftServer server, ServerPlayer player) {
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /**
     * Drops records whose owner logged out, respawned into a new instance, became alive, or left
     * the recorded dimension. A chunk-unloaded noSave marker is recreated (same token/time) once
     * its already-loaded chunk is back; it never force-loads a chunk.
     */
    private static void pruneCorpses(MinecraftServer server, ServerState state) {
        for (UUID ownerId : new ArrayList<>(state.corpses.keySet())) {
            CorpseRecord record = state.corpses.get(ownerId);
            if (record == null) {
                continue;
            }
            ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
            ServerLevel ownerLevel = current == null ? null : current.serverLevel();
            if (current != record.player || !record.player.isDeadOrDying() || ownerLevel == null
                    || !ownerLevel.dimension().equals(record.dimension)) {
                removeCorpse(state, ownerId);
                continue;
            }
            if (record.corpse != null && !record.corpse.isRemoved()) {
                continue;
            }
            long now = ownerLevel.getGameTime();
            if (now < record.nextRecreateTick
                    || !ownerLevel.hasChunkAt(BlockPos.containing(record.position))) {
                continue;
            }
            RevivalCorpse fresh = new RevivalCorpse(RevivalEntities.CORPSE.get(), ownerLevel);
            fresh.setUUID(record.token);
            fresh.setPos(record.position.x, record.position.y, record.position.z);
            fresh.setYRot(record.yaw);
            fresh.configure(ownerId, record.name, record.penguin, record.deathTick);
            if (ownerLevel.addFreshEntity(fresh)) {
                record.corpse = fresh;
            } else {
                fresh.discard();
                record.nextRecreateTick = now + RECREATE_RETRY_TICKS;
            }
        }
    }

    private static boolean lookingAt(ServerPlayer caster, Entity target) {
        Vec3 eye = caster.getEyePosition();
        Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
        double distance = delta.length();
        return distance > 0.0D && distance <= RANGE
                && delta.normalize().dot(caster.getLookAngle()) >= LOOK_DOT
                && caster.hasLineOfSight(target);
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static RevivalPackets.State packet(Channel channel, boolean active) {
        return new RevivalPackets.State(channel.level.dimension().location(), channel.caster.getId(),
                channel.caster.getUUID(), channel.targetKey, channel.kind == TargetKind.MAID,
                channel.startTick, channel.session, active);
    }

    /** Explicit public bridge for the ServerPlayer mixin in another package. */
    public static DimensionTransition consumePendingRespawn(ServerPlayer player) {
        ServerState state = stateOrNull(player.getServer());
        return state == null ? null : state.pendingRespawns.remove(player.getUUID());
    }

    private static DimensionTransition safeTransition(ServerLevel level, Vec3 origin, float yaw) {
        Vec3 safe = findSafePosition(level, origin);
        return safe == null ? null : new DimensionTransition(level, safe, Vec3.ZERO, yaw, 0.0F,
                DimensionTransition.DO_NOTHING);
    }

    /** Same-dimension, loaded, world-border-safe corpse-adjacent stand position with collision. */
    private static Vec3 findSafePosition(ServerLevel level, Vec3 origin) {
        BlockPos base = BlockPos.containing(origin);
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                            continue;
                        }
                        AABB box = new AABB(pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D,
                                pos.getX() + 0.8D, pos.getY() + 1.8D, pos.getZ() + 0.8D);
                        if (!level.noCollision(box)) {
                            continue;
                        }
                        BlockPos below = pos.below();
                        if (!level.hasChunkAt(below)
                                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                            continue;
                        }
                        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                    }
                }
            }
        }
        return null;
    }
}
