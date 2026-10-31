package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.animation.PenguinKick;
import com.luochun.kusopenguin.compat.MelodiesDanceCompat;
import com.luochun.kusopenguin.dance.DancePlaybackManager;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.Pose;
import com.luochun.kusopenguin.registry.ModEffects;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Transient AI ownership. No NoAI flag, player input or saved entity state is ever changed. */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DanceManager {
    public static double radius = 12.0;
    public static double spacing = .5;
    private static final class Participant {
        final LivingEntity entity;
        final MusicSessions.Session session;
        final Vec3 slot;
        final AABB reserved;
        int nextPathAttempt;
        Participant(LivingEntity entity, MusicSessions.Session session, Vec3 slot) {
            this.entity = entity; this.session = session; this.slot = slot;
            reserved = slot == null ? null : entity.getBoundingBox().move(slot.subtract(entity.position()))
                    .inflate(spacing * .5, 0, spacing * .5);
        }
    }
    private static final Map<MinecraftServer, Map<UUID, Participant>> SERVERS = new HashMap<>();
    private static volatile Hook hook;
    private DanceManager() {}

    /**
     * Narrow bridge for the next actor manager. It receives one start/stop per actual jukebox
     * membership change and never replaces the existing Disc.Dance broadcast.
     */
    public interface Hook {
        void onDanceStart(LivingEntity entity, MusicSessions.Session session, UUID prefsOwner);
        void onDanceStop(LivingEntity entity, MusicSessions.Session session, UUID prefsOwner);
    }

    public static void installHook(Hook next) {
        hook = next;
    }

    /**
     * Clip-preference owner for one jukebox participant: the player itself, the owning player of
     * an exact TLM maid, or the uploaded track owner for a wild mob.
     */
    public static UUID preferenceOwner(LivingEntity entity, MusicSessions.Session session) {
        if (entity instanceof Player player) return player.getUUID();
        UUID maidOwner = MelodiesDanceCompat.maidOwner(entity);
        if (maidOwner != null) return maidOwner;
        return session == null ? null : session.track.owner();
    }

    private static boolean eligible(LivingEntity entity) {
        return entity.isAlive() && !entity.isBaby() && entity.hasEffect(ModEffects.PENGUIN)
                && (PenguinEntitySupport.isSupportedType(entity.getType())
                    || PenguinEntitySupport.isProneType(entity.getType()))
                && !entity.isPassenger() && !entity.isSwimming() && !entity.isFallFlying()
                && !entity.hasPose(Pose.SITTING)
                && !(entity instanceof TamableAnimal tame && tame.isInSittingPose())
                && !(entity instanceof Cat cat && cat.isLying())
                // An actual instrument performance outranks the jukebox; a paused held instrument
                // has no PLAYING flag and may still dance.
                && !MelodiesDanceCompat.playingInstrument(entity)
                // A manual/maid-music dancer is owned by the new actor map and must not also be
                // navigated into the jukebox ring.
                && !DancePlaybackManager.ownsTransientAI(entity);
    }

    /** Consulted immediately before native serverAiStep so effect loss cannot leave AI paused. */
    public static boolean pausesAI(Mob mob) {
        // The new actor map owns its own transient pause and does not rely on the jukebox
        // eligibility predicate (which deliberately excludes those actors from the circle).
        if (DancePlaybackManager.pausesAI(mob)) return true;
        if (!(mob.level() instanceof ServerLevel level) || !eligible(mob)) return false;
        Map<UUID, Participant> members = SERVERS.get(level.getServer());
        Participant participant = members == null ? null : members.get(mob.getUUID());
        return participant != null && MusicSessions.active(level.getServer(), participant.session.id);
    }

    public static void tick(MinecraftServer server) {
        radius = DanceSettings.RADIUS.get(); spacing = DanceSettings.SPACING.get();
        Map<UUID, Participant> members = SERVERS.computeIfAbsent(server, s -> new HashMap<>());
        Iterator<Participant> old = members.values().iterator();
        while (old.hasNext()) {
            Participant p = old.next();
            if (!eligible(p.entity) || p.entity.isRemoved() || p.entity.level() != p.session.level
                    || p.entity.distanceToSqr(p.session.center()) > radius * radius
                    || !MusicSessions.active(server, p.session.id)) {
                release(p); old.remove();
            }
        }
        if (server.getTickCount() % 5 == 0) {
            // Broadphase queries visit only loaded nearby entities. Existing members keep their
            // source to prevent jitter where two jukebox radii overlap.
            for (MusicSessions.Session session : MusicSessions.all(server)) {
                AABB region = new AABB(session.source.pos()).inflate(radius, 5, radius);
                for (LivingEntity entity : session.level.getEntitiesOfClass(LivingEntity.class, region, DanceManager::eligible)) {
                    if (PenguinKick.active(entity) || members.containsKey(entity.getUUID()) || entity.distanceToSqr(session.center()) > radius * radius) continue;
                    MusicSessions.Session closest = session;
                    for (MusicSessions.Session candidate : MusicSessions.all(server)) {
                        if (candidate.level == entity.level()
                                && entity.distanceToSqr(candidate.center()) < entity.distanceToSqr(closest.center())) closest = candidate;
                    }
                    Vec3 slot = entity instanceof Player ? null : reserve((Mob) entity, closest, members.values());
                    if (!(entity instanceof Player) && slot == null) continue; // Full/unsafe ring: retry, never stack.
                    Participant p = new Participant(entity, closest, slot);
                    members.put(entity.getUUID(), p);
                    if (entity instanceof Mob mob) mob.getNavigation().stop();
                    publish(p, true);
                }
            }
        }
        for (Participant p : members.values()) {
            if (!(p.entity instanceof Mob mob) || mob.isNoAi()) continue;
            if (mob instanceof Creeper creeper) creeper.setSwellDir(-1);
            double dx = p.slot.x - mob.getX(), dz = p.slot.z - mob.getZ();
            if (dx * dx + dz * dz > .06) {
                if (server.getTickCount() >= p.nextPathAttempt) {
                    p.nextPathAttempt = server.getTickCount() + 20;
                    mob.getNavigation().moveTo(p.slot.x, p.slot.y, p.slot.z, .9);
                }
                // Vanilla AI is paused; tick just our navigation/controllers to reach the slot.
                // Actual travel, gravity and block collision remain in native LivingEntity logic.
                mob.getNavigation().tick(); mob.getMoveControl().tick();
                mob.getLookControl().tick(); mob.getJumpControl().tick();
            } else {
                mob.getNavigation().stop(); mob.zza = 0; mob.xxa = 0; mob.setSpeed(0);
                Vec3 center = p.session.center();
                float yaw = (float) (Mth.atan2(center.z - mob.getZ(), center.x - mob.getX()) * 180 / Math.PI) - 90;
                mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.yBodyRot = yaw;
            }
        }
    }

    private static Vec3 reserve(Mob mob, MusicSessions.Session session, Collection<Participant> members) {
        ServerLevel level = session.level;
        for (double ring = 2; ring <= radius - 1; ring += .75) {
            int slots = Math.max(8, (int) Math.ceil(2 * Math.PI * ring / Math.max(.5, mob.getBbWidth() + spacing)));
            for (int index = 0; index < slots; index++) {
                double angle = index * Math.PI * 2 / slots;
                double x = session.center().x + Math.cos(angle) * ring;
                double z = session.center().z + Math.sin(angle) * ring;
                for (int offset : new int[]{0, -1, 1, -2, 2}) {
                    BlockPos below = BlockPos.containing(x, session.source.pos().getY() - 1 + offset, z);
                    var state = level.getBlockState(below);
                    var support = state.getCollisionShape(level, below);
                    if (support.isEmpty() || !state.getFluidState().isEmpty()
                            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)) continue;
                    double y = below.getY() + support.max(Direction.Axis.Y);
                    Vec3 foot = new Vec3(x, y, z);
                    AABB body = mob.getBoundingBox().move(foot.subtract(mob.position()));
                    if (!level.noCollision(mob, body)) continue;
                    AABB reservation = body.inflate(spacing * .5, 0, spacing * .5);
                    boolean occupied = false;
                    for (Participant other : members) {
                        if (other.reserved != null && other.session.id.equals(session.id)
                                && other.reserved.intersects(reservation)) { occupied = true; break; }
                    }
                    if (!occupied) return foot;
                }
            }
        }
        return null;
    }

    private static DiscPackets.Dance packet(Participant p, boolean playing) {
        return new DiscPackets.Dance(p.session.source.dimension(), p.entity.getUUID(), p.session.id,
                p.session.started, playing);
    }
    private static void publish(Participant p, boolean playing) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p.entity, packet(p, playing));
        Hook listener = hook;
        if (listener == null) return;
        UUID prefsOwner = preferenceOwner(p.entity, p.session);
        if (playing) listener.onDanceStart(p.entity, p.session, prefsOwner);
        else listener.onDanceStop(p.entity, p.session, prefsOwner);
    }
    private static void release(Participant p) {
        if (p.entity instanceof Mob mob) {
            mob.getNavigation().stop(); mob.zza = 0; mob.xxa = 0;
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        }
        publish(p, false);
    }
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Map<UUID, Participant> members = SERVERS.get(player.getServer());
        Participant p = members == null ? null : members.get(event.getTarget().getUUID());
        if (p != null) PacketDistributor.sendToPlayer(player, packet(p, true));
    }
    public static void clear(MinecraftServer server) { SERVERS.remove(server); }
}
