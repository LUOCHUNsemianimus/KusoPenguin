package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.KusoPenguin;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The native jukebox owns the record and elapsed age; this map owns transient listeners only. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MusicSessions {
    public static final double HEARING_RADIUS = 64;
    public record Source(ResourceLocation dimension, BlockPos pos) {}
    public static final class Session {
        public final Source source;
        public final UUID id = UUID.randomUUID();
        public final ServerLevel level;
        public final MusicLibrary.Track track;
        public final long started;
        private final Set<UUID> listeners = new HashSet<>();
        private int lastSeen, lastAge;
        private Session(ServerLevel level, BlockPos pos, MusicLibrary.Track track, int age) {
            this.level = level;
            this.source = new Source(level.dimension().location(), pos.immutable());
            this.track = track;
            this.started = level.getGameTime() - age;
            this.lastAge = age;
            this.lastSeen = level.getServer().getTickCount();
        }
        public Vec3 center() { return Vec3.atCenterOf(source.pos()); }
        public DiscPackets.Playback packet(boolean playing) {
            return new DiscPackets.Playback(source.dimension(), source.pos(), id, track.id(),
                    started, track.durationTicks(), playing);
        }
    }
    private static final Map<MinecraftServer, Map<Source, Session>> SERVERS = new HashMap<>();
    private MusicSessions() {}

    /** Called only by native jukebox setItem/tick, never scans or loads chunks. */
    public static void observe(JukeboxBlockEntity jukebox) {
        if (!(jukebox.getLevel() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Map<Source, Session> sessions = SERVERS.computeIfAbsent(server, s -> new HashMap<>());
        Source source = new Source(level.dimension().location(), jukebox.getBlockPos().immutable());
        Session old = sessions.get(source);
        String trackId = MusicItems.trackId(jukebox.getTheItem());
        MusicLibrary.Track track = trackId.isEmpty() ? null : MusicLibrary.get(server).get(trackId);
        var player = jukebox.getSongPlayer();
        int age = (int) Math.min(Integer.MAX_VALUE, player.getTicksSinceSongStarted());
        if (track == null || !player.isPlaying() || age >= track.durationTicks()) {
            if (track != null && player.isPlaying()) player.stop(level, jukebox.getBlockState());
            if (old != null) stop(server, sessions.remove(source));
            return;
        }
        // A replay of the same disc is a NEW session. Its old download cannot restart the new
        // record accidentally, and listeners receive a fresh native start timestamp.
        if (old == null || !old.track.id().equals(trackId) || age < old.lastAge) {
            if (old != null) stop(server, old);
            old = new Session(level, source.pos(), track, age);
            sessions.put(source, old);
        }
        old.lastSeen = server.getTickCount();
        old.lastAge = age;
    }

    public static List<Session> all(MinecraftServer server) {
        Map<Source, Session> map = SERVERS.get(server);
        return map == null ? List.of() : List.copyOf(map.values());
    }

    public static boolean active(MinecraftServer server, UUID id) {
        for (Session session : all(server)) if (session.id.equals(id)) return true;
        return false;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<Source, Session> sessions = SERVERS.get(server);
        if (sessions != null) {
            Iterator<Session> iterator = sessions.values().iterator();
            while (iterator.hasNext()) {
                Session session = iterator.next();
                if (server.getTickCount() - session.lastSeen > 2) {
                    stop(server, session);
                    iterator.remove();
                    continue;
                }
                if (server.getTickCount() % 5 != 0) continue;
                Set<UUID> nearby = new HashSet<>();
                for (ServerPlayer player : session.level.players()) {
                    if (player.distanceToSqr(session.center()) > HEARING_RADIUS * HEARING_RADIUS) continue;
                    nearby.add(player.getUUID());
                    if (session.listeners.add(player.getUUID()))
                        PacketDistributor.sendToPlayer(player, session.packet(true));
                }
                Iterator<UUID> listeners = session.listeners.iterator();
                while (listeners.hasNext()) {
                    UUID listener = listeners.next();
                    if (nearby.contains(listener)) continue;
                    ServerPlayer player = server.getPlayerList().getPlayer(listener);
                    if (player != null) PacketDistributor.sendToPlayer(player, session.packet(false));
                    listeners.remove();
                }
            }
        }
        DanceManager.tick(server);
    }

    private static void stop(MinecraftServer server, Session session) {
        for (UUID listener : session.listeners) {
            ServerPlayer player = server.getPlayerList().getPlayer(listener);
            if (player != null) PacketDistributor.sendToPlayer(player, session.packet(false));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SERVERS.remove(event.getServer());
        DanceManager.clear(event.getServer());
    }
}
