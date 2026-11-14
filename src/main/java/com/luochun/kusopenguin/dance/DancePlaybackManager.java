package com.luochun.kusopenguin.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.animation.PenguinEntitySupport;
import com.luochun.kusopenguin.animation.PenguinKick;
import com.luochun.kusopenguin.compat.MelodiesDanceCompat;
import com.luochun.kusopenguin.compat.SiliconeDollsCompat;
import com.luochun.kusopenguin.music.DanceManager;
import com.luochun.kusopenguin.music.MusicSessions;
import com.luochun.kusopenguin.registry.ModEffects;
import com.luochun.kusopenguin.revival.RevivalManager;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * One bounded, server-authoritative actor map for the new dance selection channels.
 *
 * <p>This manager never owns movement, damage or revival gameplay. It only owns transient visual
 * playback state and the temporary AI pause of a manually selected own maid. Jukebox membership and
 * revival channels remain owned by {@link DanceManager}/{@link MusicSessions} and
 * {@link RevivalManager}; they publish narrow start/stop callbacks so this map can select a visual
 * clip without duplicating their circle AI, timers, loot or health logic.</p>
 *
 * <p>Priority for the single published visual state is REVIVAL &gt; MANUAL &gt; MAID_MUSIC &gt;
 * MUSIC. The documented {@link Source#priority()} order remains unchanged for wire compatibility;
 * revival is additionally highest here because the authoritative client revival gate must always
 * win the visible draw.</p>
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DancePlaybackManager {
    /** Hard upper bound of transient actors per running server. */
    public static final int MAX_ACTORS = 256;
    /** Manual maid selection range, checked on the server regardless of any client raycast. */
    public static final double MANUAL_MAID_RANGE = 4.0D;
    /** Direct K boss view ray length. Range is measured to the native bounding box, not centre. */
    private static final double MANUAL_BOSS_RANGE = 4.0D;
    /** Small network-timing tolerance around the actual boss box for the view ray test. */
    private static final double BOSS_RAY_TOLERANCE = 0.25D;
    /** Maid-music trigger range around the performing maid. */
    public static final double MAID_MUSIC_RANGE = 12.0D;
    private static final int SCAN_INTERVAL = 5;
    /** Display selection order; see the class comment for the revival override. */
    private static final Source[] DISPLAY_ORDER = java.util.Arrays.stream(Source.values())
            .sorted(java.util.Comparator.comparingInt(Source::priority)).toArray(Source[]::new);

    private static final Map<MinecraftServer, ServerState> STATES = new HashMap<>();

    private DancePlaybackManager() {
    }

    private static final class Layer {
        final UUID session;
        final Source source;
        final String clipId;
        final long startTick;
        final UUID owner;
        /** Jukebox session id or performing-maid UUID, used to reject a stale source stop. */
        final UUID sourceKey;
        final LivingEntity performer;
        final int revivalSession;

        private Layer(UUID session, Source source, String clipId, long startTick, UUID owner,
                      UUID sourceKey, LivingEntity performer, int revivalSession) {
            this.session = session;
            this.source = source;
            this.clipId = clipId;
            this.startTick = Math.max(0L, startTick);
            this.owner = owner;
            this.sourceKey = sourceKey;
            this.performer = performer;
            this.revivalSession = revivalSession;
        }
    }

    private static final class Actor {
        final UUID entityId;
        final ResourceLocation dimension;
        LivingEntity entity;
        final EnumMap<Source, Layer> layers = new EnumMap<>(Source.class);
        Layer published;

        private Actor(UUID entityId, ResourceLocation dimension) {
            this.entityId = entityId;
            this.dimension = dimension;
        }
    }

    private static final class ServerState {
        final Map<UUID, Actor> actors = new HashMap<>();
    }

    private static final DanceManager.Hook MUSIC_HOOK = new DanceManager.Hook() {
        @Override
        public void onDanceStart(LivingEntity entity, MusicSessions.Session session, UUID prefsOwner) {
            musicStart(entity, session, prefsOwner);
        }

        @Override
        public void onDanceStop(LivingEntity entity, MusicSessions.Session session, UUID prefsOwner) {
            musicStop(entity, session);
        }
    };

    private static final RevivalManager.DanceHook REVIVAL_HOOK = new RevivalManager.DanceHook() {
        @Override
        public void onRevivalStart(ServerPlayer caster, long startTick, int session) {
            revivalStart(caster, startTick, session);
        }

        @Override
        public void onRevivalEnd(ServerPlayer caster, int session) {
            revivalEnd(caster, session);
        }
    };

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        DanceServer.installControl(DancePlaybackManager::handleControl);
        DanceManager.installHook(MUSIC_HOOK);
        RevivalManager.installDanceHook(REVIVAL_HOOK);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
        DanceServer.installControl(null);
        DanceManager.installHook(null);
        RevivalManager.installDanceHook(null);
    }

    /** Consulted by the existing Mob AI mixin so a manual/maid-music dancer is transiently paused. */
    public static boolean pausesAI(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)
                || PenguinEntitySupport.isDanceBoss(mob.getType())
                || !mob.hasEffect(ModEffects.PENGUIN)) {
            return false;
        }
        ServerState state = STATES.get(level.getServer());
        Actor actor = state == null ? null : state.actors.get(mob.getUUID());
        return actor != null && (actor.layers.containsKey(Source.MANUAL)
                || actor.layers.containsKey(Source.MAID_MUSIC));
    }

    /** True while this entity is owned by a transient AI-pausing dance source. */
    public static boolean ownsTransientAI(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)
                || PenguinEntitySupport.isDanceBoss(entity.getType())) {
            return false;
        }
        ServerState state = STATES.get(level.getServer());
        Actor actor = state == null ? null : state.actors.get(entity.getUUID());
        return actor != null && (actor.layers.containsKey(Source.MANUAL)
                || actor.layers.containsKey(Source.MAID_MUSIC));
    }

    // ------------------------------------------------------------------ control

    private static void handleControl(ServerPlayer player, DancePackets.Control control) {
        if (player == null || control == null || player.getServer() == null) {
            return;
        }
        if (control.action() == DancePackets.Action.PLAY) {
            play(player, control);
        } else if (control.action() == DancePackets.Action.STOP) {
            stop(player, control);
        }
    }

    private static void play(ServerPlayer player, DancePackets.Control control) {
        ServerLevel level = player.serverLevel();
        LivingEntity target;
        boolean protectedTarget = false;
        if (control.target().equals(player.getUUID())) {
            target = player;
        } else {
            Entity found = level.getEntity(control.target());
            if (!(found instanceof LivingEntity living)) {
                reply(player, control, false, "message.kuso_penguin.dance.target_invalid");
                return;
            }
            if (PenguinEntitySupport.isDanceBoss(living.getType())) {
                // Exact three-boss route: the wheel's selected builtin/uploaded clip still passes
                // the target/owner/range/LOS/effect/busy gates and the library selectable check
                // below; only the previous cycle-only restriction is removed.
                if (!living.isAlive() || living.isRemoved()
                        || !living.hasEffect(ModEffects.PENGUIN)) {
                    reply(player, control, false, "message.kuso_penguin.dance.effect_required");
                    return;
                }
                if (!bossInView(player, living)) {
                    reply(player, control, false, "message.kuso_penguin.dance.target_range");
                    return;
                }
                target = living;
                protectedTarget = true;
            } else if (SiliconeDollsCompat.isDanceTarget(living)) {
                // Another exact manual target: same alive/transformed/same-level/range/view-ray
                // gates as the boss route, then the shared busy/selectable/owner checks below.
                if (living.level() != player.level() || !living.isAlive() || living.isRemoved()
                        || !living.hasEffect(ModEffects.PENGUIN)) {
                    reply(player, control, false, "message.kuso_penguin.dance.effect_required");
                    return;
                }
                if (!bossInView(player, living)) {
                    reply(player, control, false, "message.kuso_penguin.dance.target_range");
                    return;
                }
                target = living;
                protectedTarget = true;
            } else if (MelodiesDanceCompat.isMaid(living)) {
                UUID owner = MelodiesDanceCompat.maidOwner(living);
                if (owner == null || !owner.equals(player.getUUID())) {
                    reply(player, control, false, "message.kuso_penguin.dance.not_owner");
                    return;
                }
                if (!living.isAlive() || living.isRemoved()
                        || !living.hasEffect(ModEffects.PENGUIN)) {
                    reply(player, control, false, "message.kuso_penguin.dance.effect_required");
                    return;
                }
                if (player.distanceToSqr(living) > MANUAL_MAID_RANGE * MANUAL_MAID_RANGE
                        || !player.hasLineOfSight(living)) {
                    reply(player, control, false, "message.kuso_penguin.dance.target_range");
                    return;
                }
                target = living;
            } else {
                reply(player, control, false, "message.kuso_penguin.dance.target_invalid");
                return;
            }
        }
        if (!player.isAlive() || !player.hasEffect(ModEffects.PENGUIN)) {
            reply(player, control, false, "message.kuso_penguin.dance.effect_required");
            return;
        }
        if (RevivalManager.isChanneling(player)) {
            reply(player, control, false, "message.kuso_penguin.dance.busy");
            return;
        }
        if (MelodiesDanceCompat.playingInstrument(target) || PenguinKick.active(target)) {
            reply(player, control, false, "message.kuso_penguin.dance.busy");
            return;
        }
        DanceLibrary library = DanceLibrary.get(player.getServer());
        if (control.clipId().isEmpty() || !library.selectable(player.getUUID(), control.clipId())) {
            reply(player, control, false, "message.kuso_penguin.dance.not_owner");
            return;
        }

        ServerState state = state(player.getServer());
        Actor actor = state.actors.get(target.getUUID());
        Layer existing = actor == null ? null : actor.layers.get(Source.MANUAL);
        if (protectedTarget && existing != null && !existing.owner.equals(player.getUUID())) {
            reply(player, control, false, "message.kuso_penguin.dance.not_owner");
            return;
        }
        if (existing != null && existing.owner.equals(player.getUUID())
                && existing.clipId.equals(control.clipId())) {
            // Repeating the exact same manual clip toggles it off.
            removeLayer(actor, Source.MANUAL);
            reply(player, control, true, "message.kuso_penguin.dance.stopped");
            return;
        }
        if (!addLayer(player.getServer(), target, Source.MANUAL, control.clipId(),
                player.getUUID(), level.getGameTime(), null, null, 0)) {
            reply(player, control, false, "message.kuso_penguin.dance.busy");
            return;
        }
        // ACK only after the authoritative layer exists and its playback was published.
        reply(player, control, true, "message.kuso_penguin.dance.started");
    }

    /** Server view ray against the actual native box, with no large-boss centre-distance test. */
    private static boolean bossInView(ServerPlayer player, LivingEntity boss) {
        if (!player.hasLineOfSight(boss)) {
            return false;
        }
        AABB box = boss.getBoundingBox().inflate(BOSS_RAY_TOLERANCE);
        Vec3 eye = player.getEyePosition();
        if (box.contains(eye)) {
            return true;
        }
        Vec3 end = eye.add(player.getViewVector(1.0F).normalize().scale(MANUAL_BOSS_RANGE));
        return box.clip(eye, end).isPresent();
    }

    private static void stop(ServerPlayer player, DancePackets.Control control) {
        ServerState state = STATES.get(player.getServer());
        Actor actor = state == null ? null : state.actors.get(control.target());
        Layer existing = actor == null ? null : actor.layers.get(Source.MANUAL);
        if (existing == null) {
            // Idempotent stop: nothing is faked, no other source is touched.
            reply(player, control, true, "message.kuso_penguin.dance.stopped");
            return;
        }
        // Owner check deliberately does not require the effect or a living target: an effect loss
        // must not lock a stale manual session in place.
        if (!existing.owner.equals(player.getUUID())) {
            reply(player, control, false, "message.kuso_penguin.dance.not_owner");
            return;
        }
        removeLayer(actor, Source.MANUAL);
        reply(player, control, true, "message.kuso_penguin.dance.stopped");
    }

    private static void reply(ServerPlayer player, DancePackets.Control control, boolean success,
                              String message) {
        PacketDistributor.sendToPlayer(player, new DancePackets.Reply(control.action(),
                control.ticket(), success, message, control.clipId(), 0));
    }

    // ------------------------------------------------------------------ layers

    private static boolean addLayer(MinecraftServer server, LivingEntity entity, Source source,
                                    String clipId, UUID owner, long startTick, UUID sourceKey,
                                    LivingEntity performer, int revivalSession) {
        if (server == null || entity == null || clipId == null || clipId.isEmpty()) {
            return false;
        }
        ServerState state = state(server);
        Actor actor = state.actors.get(entity.getUUID());
        if (actor == null) {
            if (state.actors.size() >= MAX_ACTORS) {
                return false;
            }
            actor = new Actor(entity.getUUID(), entity.level().dimension().location());
            state.actors.put(actor.entityId, actor);
        }
        actor.entity = entity;
        Layer layer = new Layer(UUID.randomUUID(), source, clipId, startTick, owner, sourceKey,
                performer, revivalSession);
        actor.layers.put(source, layer);
        publish(actor);
        // Exact visual-only bosses keep their native navigation/AI/combat; previous maid behavior
        // is unchanged because only the three dance-boss registry ids are excluded.
        if ((source == Source.MANUAL || source == Source.MAID_MUSIC)
                && !PenguinEntitySupport.isDanceBoss(entity.getType())) {
            stopNavigation(entity);
        }
        return true;
    }

    private static void removeLayer(Actor actor, Source source) {
        if (actor != null && actor.layers.remove(source) != null) {
            publish(actor);
        }
    }

    private static Layer selected(Actor actor) {
        for (Source source : DISPLAY_ORDER) {
            Layer layer = actor.layers.get(source);
            if (layer != null) {
                return layer;
            }
        }
        return null;
    }

    /** Publishes only when the visible layer changes; overridden layers are never stopped. */
    private static void publish(Actor actor) {
        Layer next = selected(actor);
        if (next == null) {
            if (actor.published != null) {
                send(actor, actor.published, false);
                actor.published = null;
            }
            return;
        }
        if (actor.published != null && actor.published.session.equals(next.session)) {
            actor.published = next;
            return;
        }
        actor.published = next;
        send(actor, next, true);
    }

    private static void send(Actor actor, Layer layer, boolean playing) {
        LivingEntity entity = actor.entity;
        if (entity == null || layer == null || layer.clipId.isEmpty()) {
            return;
        }
        DancePackets.Playback payload = new DancePackets.Playback(actor.dimension, actor.entityId,
                layer.session, layer.clipId, layer.startTick, layer.source, playing);
        // A SiliconeDolls fake player has a dummy self connection; trackers only, never send to it.
        if (SiliconeDollsCompat.isDanceTarget(entity)) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        }
    }

    private static void sendTo(ServerPlayer player, Actor actor, boolean playing) {
        Layer layer = actor.published;
        if (layer == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DancePackets.Playback(actor.dimension, actor.entityId, layer.session,
                        layer.clipId, layer.startTick, layer.source, playing));
    }

    private static void stopNavigation(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            // Navigation stop only; target, inventory, equipment and the persisted NoAI flag are
            // deliberately untouched.
            mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ jukebox hook

    private static void musicStart(LivingEntity entity, MusicSessions.Session session,
                                   UUID prefsOwner) {
        if (entity == null || session == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        // Players and exact TLM maids keep the MUSIC channel. Other jukebox participants (wild
        // animals and other supported creatures) use the independent animal channel owned by the
        // uploaded track's owner. The selection is captured once per dance session, matching the
        // existing trigger semantics; later preference edits apply from the next session.
        UUID owner;
        Trigger trigger;
        if (entity instanceof Player) {
            owner = prefsOwner != null ? prefsOwner : entity.getUUID();
            trigger = Trigger.MUSIC;
        } else if (MelodiesDanceCompat.isMaid(entity)) {
            owner = prefsOwner != null ? prefsOwner : session.track.owner();
            trigger = Trigger.MUSIC;
        } else {
            owner = session.track.owner();
            trigger = Trigger.ANIMAL_MUSIC;
        }
        String clip = selection(server, owner, trigger);
        addLayer(server, entity, Source.MUSIC, clip, owner, session.started, session.id, null, 0);
    }

    private static void musicStop(LivingEntity entity, MusicSessions.Session session) {
        if (entity == null || session == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = STATES.get(level.getServer());
        Actor actor = state == null ? null : state.actors.get(entity.getUUID());
        Layer layer = actor == null ? null : actor.layers.get(Source.MUSIC);
        // A stale source stop can only remove its own jukebox session.
        if (layer != null && session.id.equals(layer.sourceKey)) {
            removeLayer(actor, Source.MUSIC);
        }
    }

    // ------------------------------------------------------------------ revival hook

    private static void revivalStart(ServerPlayer caster, long startTick, int session) {
        MinecraftServer server = caster.getServer();
        if (server == null) {
            return;
        }
        String clip = selection(server, caster.getUUID(), Trigger.REVIVAL);
        addLayer(server, caster, Source.REVIVAL, clip, caster.getUUID(), startTick, null, null,
                session);
    }

    private static void revivalEnd(ServerPlayer caster, int session) {
        MinecraftServer server = caster.getServer();
        ServerState state = server == null ? null : STATES.get(server);
        Actor actor = state == null ? null : state.actors.get(caster.getUUID());
        Layer layer = actor == null ? null : actor.layers.get(Source.REVIVAL);
        if (layer != null && layer.revivalSession == session) {
            removeLayer(actor, Source.REVIVAL);
        }
    }

    // ------------------------------------------------------------------ tick/scan

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerState state = STATES.get(server);
        if (state == null) {
            return;
        }
        boolean scan = server.getTickCount() % SCAN_INTERVAL == 0;
        Iterator<Map.Entry<UUID, Actor>> iterator = state.actors.entrySet().iterator();
        while (iterator.hasNext()) {
            Actor actor = iterator.next().getValue();
            if (!valid(server, actor)) {
                send(actor, actor.published, false);
                actor.published = null;
                iterator.remove();
                continue;
            }
            // Deleting an owner's registration ends their active upload-based selection too.
            // Cached viewers must not keep an AI-pausing dance that new viewers cannot fetch.
            actor.layers.entrySet().removeIf(entry -> !DanceLibrary.isBuiltin(entry.getValue().clipId)
                    && !DanceLibrary.get(server).selectable(entry.getValue().owner, entry.getValue().clipId));
            // Death/effect/removal/dimension were handled above. Kick and an actual instrument
            // performance stop every non-revival source; revival remains the authoritative gate.
            if (PenguinKick.active(actor.entity)
                    || MelodiesDanceCompat.playingInstrument(actor.entity)) {
                actor.layers.remove(Source.MANUAL);
                actor.layers.remove(Source.MAID_MUSIC);
                actor.layers.remove(Source.MUSIC);
            }
            Layer maidMusic = actor.layers.get(Source.MAID_MUSIC);
            if (maidMusic != null && !validMaidMusic(actor, maidMusic)) {
                actor.layers.remove(Source.MAID_MUSIC);
            }
            if (scan) {
                Layer music = actor.layers.get(Source.MUSIC);
                if (music != null && (music.sourceKey == null
                        || !MusicSessions.active(server, music.sourceKey))) {
                    actor.layers.remove(Source.MUSIC);
                }
            }
            publish(actor);
            if (actor.layers.isEmpty()) {
                iterator.remove();
            }
        }
        if (scan) {
            scanMaidMusic(server, state);
        }
    }

    private static boolean valid(MinecraftServer server, Actor actor) {
        LivingEntity entity = actor.entity;
        if (entity == null || entity.isRemoved() || !entity.isAlive()
                || !entity.hasEffect(ModEffects.PENGUIN)) {
            return false;
        }
        return entity.level() instanceof ServerLevel level
                && level.getServer() == server
                && level.dimension().location().equals(actor.dimension);
    }

    private static boolean validMaidMusic(Actor actor, Layer layer) {
        LivingEntity performer = layer.performer;
        return performer != null && !performer.isRemoved() && performer.isAlive()
                && performer.level() == actor.entity.level()
                && MelodiesDanceCompat.eligibleMaidMusicDancer(actor.entity)
                && MelodiesDanceCompat.playingUploadedMidi(performer)
                && performer.distanceToSqr(actor.entity)
                    <= MAID_MUSIC_RANGE * MAID_MUSIC_RANGE;
    }

    /**
     * One loaded-entity pass per level every five ticks. It never force-loads a chunk and performs
     * no disk/hash work: a stable performing source keeps its original start tick until it leaves
     * range, stops playing or the dancer loses the effect.
     */
    private static void scanMaidMusic(MinecraftServer server, ServerState state) {
        for (ServerLevel level : server.getAllLevels()) {
            List<LivingEntity> maids = new ArrayList<>();
            List<LivingEntity> performers = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living) || !MelodiesDanceCompat.isMaid(living)
                        || living.isRemoved() || !living.isAlive()) {
                    continue;
                }
                maids.add(living);
                if (MelodiesDanceCompat.playingUploadedMidi(living)) {
                    performers.add(living);
                }
            }
            if (maids.isEmpty() || performers.isEmpty()) {
                continue;
            }
            for (LivingEntity dancer : maids) {
                if (!dancer.hasEffect(ModEffects.PENGUIN)
                        || MelodiesDanceCompat.playingInstrument(dancer)
                        || !MelodiesDanceCompat.eligibleMaidMusicDancer(dancer)) {
                    continue;
                }
                Actor actor = state.actors.get(dancer.getUUID());
                Layer existing = actor == null ? null : actor.layers.get(Source.MAID_MUSIC);
                if (existing != null && stableSource(dancer, existing.performer)) {
                    continue;
                }
                LivingEntity best = null;
                double bestDistance = MAID_MUSIC_RANGE * MAID_MUSIC_RANGE;
                for (LivingEntity performer : performers) {
                    if (performer == dancer) {
                        continue;
                    }
                    double distance = performer.distanceToSqr(dancer);
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        best = performer;
                    }
                }
                if (best == null) {
                    continue;
                }
                UUID owner = MelodiesDanceCompat.maidOwner(dancer);
                if (owner == null) {
                    continue;
                }
                String clip = selection(server, owner, Trigger.MAID_MUSIC);
                addLayer(server, dancer, Source.MAID_MUSIC, clip, owner, level.getGameTime(),
                        best.getUUID(), best, 0);
            }
        }
    }

    private static boolean stableSource(LivingEntity dancer, LivingEntity performer) {
        return performer != null && !performer.isRemoved() && performer.isAlive()
                && performer.level() == dancer.level()
                && MelodiesDanceCompat.playingUploadedMidi(performer)
                && performer.distanceToSqr(dancer)
                    <= MAID_MUSIC_RANGE * MAID_MUSIC_RANGE;
    }

    private static String selection(MinecraftServer server, UUID owner, Trigger trigger) {
        if (owner == null) {
            return trigger.defaultClip();
        }
        DanceLibrary library = DanceLibrary.get(server);
        Prefs prefs = library.preferences(owner);
        String clip = switch (trigger) {
            case MUSIC -> prefs.music();
            case REVIVAL -> prefs.revival();
            case MAID_MUSIC -> prefs.maidMusic();
            case ANIMAL_MUSIC -> prefs.animalMusic();
        };
        if (clip == null || clip.isEmpty()) {
            return trigger.defaultClip();
        }
        return library.selectable(owner, clip) ? clip : trigger.defaultClip();
    }

    // ------------------------------------------------------------------ tracking/lifecycle

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        sendFakeTargetMarker(player, target, true);
        ServerState state = player.getServer() == null ? null : STATES.get(player.getServer());
        Actor actor = state == null ? null : state.actors.get(target.getUUID());
        if (actor != null) {
            sendTo(player, actor, true);
        }
    }

    @SubscribeEvent
    public static void stopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        sendFakeTargetMarker(player, target, false);
        ServerState state = player.getServer() == null ? null : STATES.get(player.getServer());
        Actor actor = state == null ? null : state.actors.get(target.getUUID());
        if (actor != null) {
            // Release the tracker's view without touching the authoritative actor.
            sendTo(player, actor, false);
        }
    }

    /**
     * Pushes the sole client-side fake identity for an exact non-shadow SiliconeDolls target. The
     * channel probe skips dummy/non-modded recipients; the marker carries no client authority.
     */
    private static void sendFakeTargetMarker(ServerPlayer recipient, LivingEntity target,
                                             boolean tracked) {
        if (recipient == null || target == null || SiliconeDollsCompat.isDanceTarget(recipient)
                || !SiliconeDollsCompat.isDanceTarget(target)
                || recipient.connection == null
                || !NetworkRegistry.hasChannel(recipient.connection,
                        DancePackets.FakeTargetMarker.TYPE.id())) {
            return;
        }
        PacketDistributor.sendToPlayer(recipient, new DancePackets.FakeTargetMarker(
                target.getUUID(), target.level().dimension().location(), tracked));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        ServerState state = STATES.get(player.getServer());
        if (state == null) {
            return;
        }
        UUID owner = player.getUUID();
        Iterator<Map.Entry<UUID, Actor>> iterator = state.actors.entrySet().iterator();
        while (iterator.hasNext()) {
            Actor actor = iterator.next().getValue();
            actor.layers.entrySet().removeIf(entry ->
                    (entry.getKey() == Source.MANUAL || entry.getKey() == Source.MAID_MUSIC
                            || entry.getKey() == Source.MUSIC)
                            && owner.equals(entry.getValue().owner));
            publish(actor);
            if (actor.layers.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }
}
