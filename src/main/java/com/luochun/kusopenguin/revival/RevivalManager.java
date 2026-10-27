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
 * token). The maid film ritual is a separate per-caster channel opened by a hold-control packet;
 * it is keyed by a random client hold token and never touches the player channel. Both reuse the
 * same state packet, animation clock and effect/respawn helpers.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RevivalManager {
    /** Corpse ritual: one K press starts it and a second K press cancels it. */
    public static final int DURATION_TICKS = 160;
    /** Held-film ritual: the key must stay held for five seconds. */
    public static final int FILM_DURATION_TICKS = 100;
    /**
     * Hold lease. The client pulses every five ticks; the channel becomes invalid once this many
     * server ticks have elapsed without a matching PULSE. The timeout is deliberately much shorter
     * than the ritual so releasing K cannot finish the revival even if STOP is lost.
     */
    public static final int FILM_LEASE_TICKS = 15;
    private static final double RANGE = 4.0D;
    private static final double LOOK_DOT = 0.82D;
    private static final double MOVE_LIMIT = 0.75D;
    private static final int PROGRESS_INTERVAL = 20;
    private static final int RECREATE_RETRY_TICKS = 20;
    private static final Map<MinecraftServer, ServerState> STATES = new HashMap<>();

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
        final ServerPlayer targetPlayer;
        final Vec3 startPos;
        final long startTick;
        final int session;

        Channel(ServerLevel level, ServerPlayer caster, UUID targetKey, ServerPlayer targetPlayer,
                Vec3 startPos, long startTick, int session) {
            this.level = level;
            this.caster = caster;
            this.targetKey = targetKey;
            this.targetPlayer = targetPlayer;
            this.startPos = startPos;
            this.startTick = startTick;
            this.session = session;
        }
    }

    /**
     * Held-film channel. The film is a private snapshot; the live main/off slot is compared with
     * it on every pulse and again at commit. {@code leaseTick} is refreshed only by a matching
     * PULSE, so a released key, lost packet, or disconnected client cancels within the lease.
     */
    private static final class FilmChannel {
        final ServerLevel level;
        final ServerPlayer caster;
        final UUID token;
        final MaidRevivalCompat.HeldFilm film;
        final Vec3 startPos;
        final long startTick;
        final int session;
        long leaseTick;

        FilmChannel(ServerLevel level, ServerPlayer caster, UUID token,
                    MaidRevivalCompat.HeldFilm film, Vec3 startPos, long startTick, int session) {
            this.level = level;
            this.caster = caster;
            this.token = token;
            this.film = film;
            this.startPos = startPos;
            this.startTick = startTick;
            this.session = session;
            this.leaseTick = startTick;
        }
    }

    private static final class ServerState {
        final Map<UUID, CorpseRecord> corpses = new HashMap<>();
        final Map<UUID, Channel> channels = new HashMap<>();
        final Map<UUID, Channel> reservations = new HashMap<>();
        final Map<UUID, FilmChannel> filmChannels = new HashMap<>();
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
        FilmChannel filmChannel = state.filmChannels.get(player.getUUID());
        if (filmChannel != null) {
            cancelFilm(state, filmChannel,
                    Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
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
        FilmChannel film = state.filmChannels.get(player.getUUID());
        if (film != null) {
            cancelFilm(state, film,
                    Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
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
        FilmChannel film = state.filmChannels.get(caster.getUUID());
        if (film != null) {
            PacketDistributor.sendToPlayer(tracker, filmPacket(film, true));
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
        for (FilmChannel film : new ArrayList<>(state.filmChannels.values())) {
            if (state.filmChannels.get(film.caster.getUUID()) != film) {
                continue;
            }
            long now = film.level.getGameTime();
            long elapsed = now - film.startTick;
            if (elapsed < 0 || now - film.leaseTick >= FILM_LEASE_TICKS || !validFilm(film)) {
                cancelFilm(state, film,
                        Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
                continue;
            }
            if (elapsed % PROGRESS_INTERVAL == 0) {
                int seconds = (int) Math.max(0,
                        (FILM_DURATION_TICKS - elapsed + 19) / 20);
                film.caster.displayClientMessage(Component.translatable(
                        "message.kuso_penguin.revival.film_progress", seconds), true);
            }
            if (elapsed >= FILM_DURATION_TICKS) {
                completeFilm(state, film);
            }
        }
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
                if (channel.targetPlayer.connection != null) {
                    channel.targetPlayer.displayClientMessage(progress, true);
                }
            }
            if (elapsed >= DURATION_TICKS) {
                completePlayer(state, channel);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    /**
     * Explicit START/PULSE/STOP from the client hold. A STOP or PULSE with a stale token is
     * ignored, so it can never cancel or refresh a newer ritual; only the film map is touched,
     * never the corpse channel.
     */
    public static void control(ServerPlayer caster, UUID token, net.minecraft.world.InteractionHand hand,
                               RevivalPackets.Action action) {
        MinecraftServer server = caster.getServer();
        if (server == null || token == null || hand == null || action == null
                || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = state(server);
        switch (action) {
            case START -> startFilm(state, caster, level, token, hand);
            case PULSE -> pulseFilm(state, caster, token);
            case STOP -> stopFilm(state, caster, token);
        }
    }

    private static void startFilm(ServerState state, ServerPlayer caster, ServerLevel level,
                                  UUID token, net.minecraft.world.InteractionHand hand) {
        long now = level.getGameTime();
        FilmChannel existing = state.filmChannels.get(caster.getUUID());
        if (existing != null) {
            // A duplicated START is idempotent only for the same key hold and the same bound hand.
            // A stale or differently-bound START must not replace an active hold token.
            if (existing.token.equals(token) && existing.film.hand() == hand) {
                existing.leaseTick = now;
            }
            return;
        }
        if (state.channels.containsKey(caster.getUUID())) {
            return;
        }
        if (!caster.isAlive() || caster.isSpectator() || !caster.hasEffect(ModEffects.PENGUIN)) {
            fail(caster, "message.kuso_penguin.revival.effect_required");
            return;
        }
        MaidRevivalCompat.HeldFilm film = MaidRevivalCompat.findHeldFilm(caster, hand);
        if (film == null) {
            fail(caster, "message.kuso_penguin.revival.film_required");
            return;
        }
        FilmChannel channel = new FilmChannel(level, caster, token, film, caster.position(), now,
                state.nextSession++);
        state.filmChannels.put(caster.getUUID(), channel);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, filmPacket(channel, true));
        caster.displayClientMessage(
                Component.translatable("message.kuso_penguin.revival.film_started"), true);
    }

    private static void pulseFilm(ServerState state, ServerPlayer caster, UUID token) {
        FilmChannel channel = state.filmChannels.get(caster.getUUID());
        if (channel == null || channel.caster != caster || !channel.token.equals(token)) {
            return;
        }
        if (!validFilm(channel)) {
            cancelFilm(state, channel,
                    Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
            return;
        }
        channel.leaseTick = channel.level.getGameTime();
    }

    private static void stopFilm(ServerState state, ServerPlayer caster, UUID token) {
        FilmChannel channel = state.filmChannels.get(caster.getUUID());
        if (channel == null || channel.caster != caster || !channel.token.equals(token)) {
            return;
        }
        cancelFilm(state, channel,
                Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
    }

    /**
     * Exact death-token request from the client. The former tombstone/grave K target is gone: a
     * maid film is now revived by holding K, not by aiming at the grave.
     */
    public static void request(ServerPlayer caster, UUID target) {
        MinecraftServer server = caster.getServer();
        if (server == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = state(server);
        if (state.filmChannels.containsKey(caster.getUUID())) {
            fail(caster, "message.kuso_penguin.revival.film_busy");
            return;
        }
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
        if (record == null) {
            fail(caster, "message.kuso_penguin.revival.invalid_target");
            return;
        }
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
        start(state, new Channel(level, caster, target, targetPlayer, caster.position(),
                level.getGameTime(), state.nextSession++));
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

    /**
     * The live caster and the exact copied film stack are checked on every pulse and every tick:
     * alive/effect/dimension/hand/selected slot/item data/count/movement all take part.
     */
    private static boolean validFilm(FilmChannel channel) {
        ServerPlayer caster = channel.caster;
        return !caster.isRemoved() && caster.isAlive() && caster.connection != null
                && !caster.isSpectator()
                && caster.serverLevel() == channel.level
                && isCurrent(caster.getServer(), caster)
                && caster.hasEffect(ModEffects.PENGUIN)
                && !caster.isPassenger() && !caster.isSwimming() && !caster.isFallFlying()
                && !PenguinKick.active(caster)
                && caster.position().distanceToSqr(channel.startPos) <= MOVE_LIMIT * MOVE_LIMIT
                && MaidRevivalCompat.matches(caster, channel.film);
    }

    /** Runs only after the 100-tick hold and before any film mutation. */
    private static void completeFilm(ServerState state, FilmChannel channel) {
        if (channel.level.getGameTime() - channel.leaseTick >= FILM_LEASE_TICKS
                || !validFilm(channel)) {
            cancelFilm(state, channel,
                    Component.translatable("message.kuso_penguin.revival.film_cancelled"), true);
            return;
        }
        Vec3 safe = findSafePosition(channel.level, channel.caster.position());
        if (safe == null) {
            cancelFilm(state, channel,
                    Component.translatable("message.kuso_penguin.revival.no_safe_spot"), true);
            return;
        }
        if (!MaidRevivalCompat.resurrectFromHeld(channel.level, channel.caster, channel.film, safe)) {
            cancelFilm(state, channel,
                    Component.translatable("message.kuso_penguin.revival.maid_failed"), true);
            return;
        }
        state.filmChannels.remove(channel.caster.getUUID(), channel);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster, filmPacket(channel, false));
        channel.caster.displayClientMessage(
                Component.translatable("message.kuso_penguin.revival.film_success"), true);
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

    private static void cancelFilm(ServerState state, FilmChannel channel, Component message,
                                   boolean notify) {
        if (!state.filmChannels.remove(channel.caster.getUUID(), channel)) {
            return;
        }
        if (channel.caster.connection != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(channel.caster,
                    filmPacket(channel, false));
            if (notify) {
                channel.caster.displayClientMessage(message, true);
            }
        }
    }

    private static void cleanupPlayer(ServerState state, UUID playerId) {
        removeCorpse(state, playerId);
        Channel caster = state.channels.get(playerId);
        if (caster != null) {
            cancel(state, caster, Component.translatable("message.kuso_penguin.revival.cancelled"), false);
        }
        FilmChannel film = state.filmChannels.get(playerId);
        if (film != null) {
            cancelFilm(state, film,
                    Component.translatable("message.kuso_penguin.revival.film_cancelled"), false);
        }
        // Reservations are keyed by the death token, so find channels by target owner instead.
        cancelTargeting(state, playerId, false);
    }

    private static void cancelTargeting(ServerState state, UUID playerId, boolean notify) {
        for (Channel channel : new ArrayList<>(state.channels.values())) {
            if (channel.targetPlayer.getUUID().equals(playerId)) {
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
                channel.caster.getUUID(), channel.targetKey, false,
                channel.startTick, channel.session, active);
    }

    private static RevivalPackets.State filmPacket(FilmChannel channel, boolean active) {
        return new RevivalPackets.State(channel.level.dimension().location(), channel.caster.getId(),
                channel.caster.getUUID(), channel.token, true,
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

    /** Same-dimension, loaded, world-border-safe stand position with collision. */
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
