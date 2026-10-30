package com.luochun.kusopenguin.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.compat.MelodiesVocalCompat;
import com.luochun.kusopenguin.music.MusicLibrary;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side vocal editing session state.
 *
 * <p>Sessions are keyed by player and never own persisted vocal data: bindings remain on the held
 * item through {@link VocalData}. An edit is accepted only from the same connection, with the same
 * ticket, exact held stack object, hand, selected melody and current server fingerprint. Expired or
 * mismatched edits receive a failure state and perform no mutation.
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VocalServer {
    // Allow file selection, decoding and auditioning without expiring a live editor in one minute.
    // Exact stack/hand/connection/selection checks still guard every operation.
    private static final int SESSION_TICKS = 20 * 60 * 10;
    private static final int REQUEST_COOLDOWN_TICKS = 5;

    private static final String OPENED = "message.kuso_penguin.vocals.opened";
    private static final String NO_INSTRUMENT = "message.kuso_penguin.vocals.no_instrument";
    private static final String UNAVAILABLE = "message.kuso_penguin.vocals.unavailable";
    private static final String STALE = "message.kuso_penguin.vocals.stale";
    private static final String BUSY = "message.kuso_penguin.vocals.busy";
    private static final String INVALID = "message.kuso_penguin.vocals.invalid";
    private static final String TRACK_MISSING = "message.kuso_penguin.vocals.track_missing";
    private static final String LIMIT = "message.kuso_penguin.vocals.limit";
    private static final String SAVED = "message.kuso_penguin.vocals.saved";
    private static final String REMOVED = "message.kuso_penguin.vocals.removed";
    private static final String NOT_FOUND = "message.kuso_penguin.vocals.not_found";
    private static final String ENABLED = "message.kuso_penguin.vocals.enabled";
    private static final String DISABLED = "message.kuso_penguin.vocals.disabled";
    private static final String MICROPHONE_PLAYING = "message.kuso_penguin.microphone.play";
    private static final String MICROPHONE_PAUSED = "message.kuso_penguin.microphone.pause";

    /** Weak keys: editing state must never keep a Minecraft server alive. */
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private VocalServer() {
    }

    private static final class ServerState {
        private long tick;
        private final Map<UUID, Session> sessions = new HashMap<>();
        private final Map<UUID, Long> lastRequests = new HashMap<>();
    }

    private static final class Session {
        private final UUID ticket;
        private final InteractionHand hand;
        private final ItemStack stack;
        private final ServerGamePacketListenerImpl connection;
        private final String melodyId;
        private final String melodyHash;
        private long expiresAt;

        private Session(UUID ticket, InteractionHand hand, ItemStack stack,
                        ServerGamePacketListenerImpl connection, String melodyId,
                        String melodyHash, long expiresAt) {
            this.ticket = ticket;
            this.hand = hand;
            this.stack = stack;
            this.connection = connection;
            this.melodyId = melodyId;
            this.melodyHash = melodyHash;
            this.expiresAt = expiresAt;
        }
    }

    /** Current identity for a held stack: normal MIDI melody+fingerprint, or mic audio hash. */
    private record Selection(String melodyId, String melodyHash, VocalData.Binding binding) {
    }

    /** Chooses the main hand when it holds an instrument, otherwise the off hand. */
    public static void open(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        InteractionHand hand = MelodiesVocalCompat.isInstrument(player.getMainHandItem())
            || VocalItems.isMicrophone(player.getMainHandItem())
            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        open(player, hand);
    }

    /**
     * Opens only the requested hand through the same rate-limited OPEN request path used by the
     * network. There is deliberately no fallback to the other hand here.
     */
    public static void open(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null || player.getServer() == null) {
            return;
        }
        receive(player, new VocalPackets.Request(VocalPackets.Action.OPEN, UUID.randomUUID(),
            hand, "", 0, 1.0F, false));
    }

    public static void receive(ServerPlayer player, VocalPackets.Request request) {
        if (player == null || request == null || player.getServer() == null) {
            return;
        }
        ServerState state = state(player.getServer());
        UUID playerId = player.getUUID();
        long now = state.tick;
        Long last = state.lastRequests.get(playerId);
        if (last != null && now - last < REQUEST_COOLDOWN_TICKS) {
            Session session = state.sessions.get(playerId);
            if (session != null && session.hand == request.hand()) {
                sendSession(player, session, session.stack, currentBinding(session), false, BUSY);
            } else {
                sendFailure(player, request.hand(), request.ticket(), BUSY);
            }
            return;
        }
        state.lastRequests.put(playerId, now);

        if (request.action() == VocalPackets.Action.OPEN) {
            open(player, request.hand(), request.ticket());
            return;
        }
        Session session = requireSession(player, state, request);
        if (session == null) {
            sendFailure(player, request.hand(), request.ticket(), STALE);
            return;
        }
        switch (request.action()) {
            case BIND -> bind(player, state, session, request);
            case REMOVE -> remove(player, state, session, request);
            case TOGGLE -> toggle(player, session, request);
            case PLAY -> play(player, session);
            case PAUSE -> pause(player, session);
            case OPEN -> throw new IllegalStateException("OPEN handled above");
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerState state = stateIfPresent(event.getServer());
        if (state == null) {
            return;
        }
        state.tick++;
        state.sessions.entrySet().removeIf(entry -> state.tick >= entry.getValue().expiresAt);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        ServerState state = stateIfPresent(player.getServer());
        if (state == null) {
            return;
        }
        state.sessions.remove(player.getUUID());
        state.lastRequests.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        synchronized (VocalServer.class) {
            STATES.remove(event.getServer());
        }
    }

    private static void open(ServerPlayer player, InteractionHand hand, UUID failureTicket) {
        if (hand == null || player.getServer() == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean microphone = VocalItems.isMicrophone(stack);
        if (!microphone) {
            if (!MelodiesVocalCompat.available()) {
                sendFailure(player, hand, failureTicket, UNAVAILABLE);
                return;
            }
            if (!MelodiesVocalCompat.isInstrument(stack)) {
                sendFailure(player, hand, failureTicket, NO_INSTRUMENT);
                return;
            }
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId.length() > VocalPackets.MAX_RESOURCE_ID_CHARS) {
            sendFailure(player, hand, failureTicket, INVALID);
            return;
        }
        Selection selection = selection(stack, microphone);
        if (selection.melodyId().isEmpty()
                || (!microphone && selection.melodyHash().isEmpty())) {
            sendFailure(player, hand, failureTicket, UNAVAILABLE);
            return;
        }
        ServerState state = state(player.getServer());
        Session session = new Session(UUID.randomUUID(), hand, stack, player.connection,
            selection.melodyId(), selection.melodyHash(), state.tick + SESSION_TICKS);
        state.sessions.put(player.getUUID(), session);
        sendSession(player, session, stack, selection.binding(), true, OPENED);
    }

    private static void bind(ServerPlayer player, ServerState state, Session session,
                             VocalPackets.Request request) {
        ItemStack stack = session.stack;
        if (VocalItems.isMicrophone(stack)) {
            bindMicrophone(player, state, session, request);
            return;
        }
        VocalData.Binding existing = VocalData.binding(stack, session.melodyId,
            session.melodyHash);
        if (existing == null
                && VocalData.bindings(stack).size() >= VocalData.MAX_BINDINGS) {
            sendSession(player, session, stack, null, false, LIMIT);
            return;
        }
        MusicLibrary library = MusicLibrary.get(player.getServer());
        if (request.trackId().isEmpty() || library.get(request.trackId()) == null) {
            sendSession(player, session, stack, existing, false, TRACK_MISSING);
            return;
        }
        VocalData.Binding requested = new VocalData.Binding(session.melodyId, session.melodyHash,
            request.trackId(), request.offsetMs(), request.gain());
        VocalData.bind(stack, requested);
        VocalData.Binding saved = VocalData.binding(stack, session.melodyId, session.melodyHash);
        if (saved == null) {
            String status = VocalData.bindings(stack).size() >= VocalData.MAX_BINDINGS
                ? LIMIT : INVALID;
            sendSession(player, session, stack, existing, false, status);
            return;
        }
        sendSession(player, session, stack, saved, false, SAVED);
    }

    private static void bindMicrophone(ServerPlayer player, ServerState state, Session session,
                                       VocalPackets.Request request) {
        ItemStack stack = session.stack;
        VocalData.Binding existing = VocalData.microphoneBinding(stack);
        MusicLibrary library = MusicLibrary.get(player.getServer());
        MusicLibrary.Track track = request.trackId().isEmpty()
            ? null : library.get(request.trackId());
        if (track == null) {
            sendSession(player, session, stack, existing, false, TRACK_MISSING);
            return;
        }
        long durationMs = Math.clamp((long) track.durationTicks() * 50L,
            VocalData.MIN_MICROPHONE_DURATION_MS, VocalData.MAX_MICROPHONE_DURATION_MS);
        VocalData.configureMicrophone(stack, track.id(), (int) durationMs, request.gain());
        VocalItems.reset(stack);
        VocalData.Binding saved = VocalData.microphoneBinding(stack);
        if (saved == null) {
            sendSession(player, session, stack, existing, false, INVALID);
            return;
        }
        Session updated = new Session(session.ticket, session.hand, session.stack,
            session.connection, VocalData.MICROPHONE_MELODY, saved.melodyHash(),
            state.tick + SESSION_TICKS);
        state.sessions.put(player.getUUID(), updated);
        sendSession(player, updated, stack, saved, false, SAVED);
    }

    private static void remove(ServerPlayer player, ServerState state, Session session,
                               VocalPackets.Request request) {
        ItemStack stack = session.stack;
        if (VocalItems.isMicrophone(stack)) {
            removeMicrophone(player, state, session);
            return;
        }
        VocalData.Binding existing = VocalData.binding(stack, session.melodyId,
            session.melodyHash);
        if (existing == null) {
            sendSession(player, session, stack, null, false, NOT_FOUND);
            return;
        }
        VocalData.remove(stack, session.melodyId, session.melodyHash);
        sendSession(player, session, stack,
            VocalData.binding(stack, session.melodyId, session.melodyHash), false, REMOVED);
    }

    private static void removeMicrophone(ServerPlayer player, ServerState state,
                                         Session session) {
        ItemStack stack = session.stack;
        boolean existed = VocalData.microphoneBinding(stack) != null;
        VocalData.clearMicrophone(stack);
        VocalItems.reset(stack);
        Session updated = new Session(session.ticket, session.hand, session.stack,
            session.connection, VocalData.MICROPHONE_MELODY, "",
            state.tick + SESSION_TICKS);
        state.sessions.put(player.getUUID(), updated);
        sendSession(player, updated, stack, null, false, existed ? REMOVED : NOT_FOUND);
    }

    private static void toggle(ServerPlayer player, Session session,
                               VocalPackets.Request request) {
        ItemStack stack = session.stack;
        VocalData.setEnabled(stack, request.enabled());
        sendSession(player, session, stack, currentBinding(session), false,
            request.enabled() ? ENABLED : DISABLED);
    }

    private static void play(ServerPlayer player, Session session) {
        ItemStack stack = session.stack;
        VocalData.Binding binding = VocalData.microphoneBinding(stack);
        if (!VocalItems.isMicrophone(stack)) {
            sendSession(player, session, stack, null, false, INVALID);
            return;
        }
        if (binding == null) {
            sendSession(player, session, stack, null, false, NOT_FOUND);
            return;
        }
        if (MusicLibrary.get(player.getServer()).get(binding.trackId()) == null) {
            sendSession(player, session, stack, binding, false, TRACK_MISSING);
            return;
        }
        VocalData.setEnabled(stack, true);
        VocalItems.play(stack, player.level());
        sendSession(player, session, stack, binding, false, MICROPHONE_PLAYING);
    }

    private static void pause(ServerPlayer player, Session session) {
        ItemStack stack = session.stack;
        if (!VocalItems.isMicrophone(stack)) {
            sendSession(player, session, stack, null, false, INVALID);
            return;
        }
        VocalItems.pause(stack, player.level());
        sendSession(player, session, stack, VocalData.microphoneBinding(stack), false,
            MICROPHONE_PAUSED);
    }

    private static Session requireSession(ServerPlayer player, ServerState state,
                                          VocalPackets.Request request) {
        UUID playerId = player.getUUID();
        Session session = state.sessions.get(playerId);
        if (session == null) {
            return null;
        }
        if (state.tick >= session.expiresAt) {
            state.sessions.remove(playerId);
            return null;
        }
        if (!session.ticket.equals(request.ticket())
                || session.hand != request.hand()
                || session.connection != player.connection) {
            return null;
        }
        ItemStack current = player.getItemInHand(session.hand);
        if (current != session.stack) {
            return null;
        }
        boolean microphone = VocalItems.isMicrophone(current);
        if (microphone) {
            if (!VocalData.MICROPHONE_MELODY.equals(session.melodyId)) {
                return null;
            }
            Selection selection = selection(current, true);
            if (!session.melodyHash.equals(selection.melodyHash())) {
                return null;
            }
        } else {
            if (!MelodiesVocalCompat.isInstrument(current)) {
                return null;
            }
            String melodyId = MelodiesVocalCompat.selectedMelody(current);
            if (!session.melodyId.equals(melodyId)) {
                return null;
            }
            String melodyHash = MelodiesVocalCompat.serverFingerprint(melodyId);
            if (melodyHash.isEmpty() || !session.melodyHash.equals(melodyHash)) {
                return null;
            }
        }
        session.expiresAt = state.tick + SESSION_TICKS;
        return session;
    }

    /**
     * Selects the normal MIDI identity or the microphone's sentinel plus current audio hash.
     * Only an unconfigured microphone may have an empty hash.
     */
    private static Selection selection(ItemStack stack, boolean microphone) {
        if (microphone) {
            VocalData.Binding binding = VocalData.microphoneBinding(stack);
            String hash = binding == null ? "" : binding.melodyHash();
            return new Selection(VocalData.MICROPHONE_MELODY, hash, binding);
        }
        String melodyId = MelodiesVocalCompat.selectedMelody(stack);
        String melodyHash = melodyId.isEmpty() ? ""
            : MelodiesVocalCompat.serverFingerprint(melodyId);
        VocalData.Binding binding = melodyId.isEmpty() || melodyHash.isEmpty() ? null
            : VocalData.binding(stack, melodyId, melodyHash);
        return new Selection(melodyId, melodyHash, binding);
    }

    private static VocalData.Binding currentBinding(Session session) {
        if (VocalItems.isMicrophone(session.stack)) {
            return VocalData.microphoneBinding(session.stack);
        }
        return VocalData.binding(session.stack, session.melodyId, session.melodyHash);
    }

    private static void sendSession(ServerPlayer player, Session session, ItemStack stack,
                                    VocalData.Binding binding, boolean open, String status) {
        String itemId = stack.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        PacketDistributor.sendToPlayer(player, new VocalPackets.State(open, session.ticket,
            session.hand, itemId, session.melodyId, session.melodyHash, binding,
            VocalData.enabled(stack), status));
    }

    private static void sendFailure(ServerPlayer player, InteractionHand hand, UUID ticket,
                                    String status) {
        PacketDistributor.sendToPlayer(player, new VocalPackets.State(false, ticket, hand,
            "", "", "", null, false, status));
    }

    private static ServerState state(MinecraftServer server) {
        synchronized (VocalServer.class) {
            return STATES.computeIfAbsent(server, ignored -> new ServerState());
        }
    }

    private static ServerState stateIfPresent(MinecraftServer server) {
        synchronized (VocalServer.class) {
            return STATES.get(server);
        }
    }
}
