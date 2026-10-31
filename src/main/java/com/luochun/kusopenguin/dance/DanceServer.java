package com.luochun.kusopenguin.dance;

import com.luochun.kusopenguin.KusoPenguin;
import com.luochun.kusopenguin.registry.ModEffects;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side transport and preference state for the custom dance library.
 *
 * <p>The upload/download state machine follows the proven disc pattern (tickets, exact offsets,
 * per-connection identity, bounded worker queue, server-thread state changes) but uses its own
 * quotas and payload family. A byte buffer is allocated only after the ticket, declared size,
 * per-player count/bytes and global reservation checks. PLAY/STOP are forwarded to one
 * installable listener; the default listener explicitly reports "not implemented" and never
 * reports success.</p>
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DanceServer {
    public static final int MAX_ACTIVE_UPLOADS = 4;
    public static final int MAX_ACTIVE_DOWNLOADS = 4;
    public static final int MAX_CHUNKS_PER_PLAYER_TICK = 4;
    public static final int MAX_CHUNKS_GLOBAL_TICK = 16;
    public static final int IDLE_TIMEOUT_TICKS = 1200;
    public static final int BEGIN_COOLDOWN_TICKS = 40;
    /** At most one preference/control state change per two server ticks per player. */
    public static final int CONTROL_COOLDOWN_TICKS = 2;
    public static final int MAX_ERROR_DETAIL_CHARS = 256;
    public static final int WORKER_THREADS = 2;
    public static final int WORK_QUEUE = 8;

    private static final String PROGRESS_MARKER = "progress";

    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/DanceServer");

    /** Weak keys: transport state must never retain a stopped Minecraft server. */
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private static final BiConsumer<ServerPlayer, DancePackets.Control> UNIMPLEMENTED =
        (player, control) -> reply(player, control.action(), control.ticket(), false,
            "message.kuso_penguin.dance.unimplemented", control.clipId(), 0);

    private static volatile BiConsumer<ServerPlayer, DancePackets.Control> controlHandler =
        UNIMPLEMENTED;

    private DanceServer() {
    }

    /** Install the next-step runtime listener. It owns replying with the real result. */
    public static void installControl(BiConsumer<ServerPlayer, DancePackets.Control> handler) {
        controlHandler = handler == null ? UNIMPLEMENTED : handler;
    }

    public static void open(ServerPlayer player) {
        sendCatalog(player, true);
    }

    /** Own catalog (max five) plus preferences and builtin metadata. */
    public static void sendCatalog(ServerPlayer player, boolean open) {
        if (player == null || player.getServer() == null) {
            return;
        }
        DanceLibrary library = DanceLibrary.get(player.getServer());
        PacketDistributor.sendToPlayer(player, new DancePackets.Catalog(open,
            library.preferences(player.getUUID()), DanceLibrary.builtins(),
            library.ownedCatalog(player.getUUID())));
    }

    /** Current sanitized preferences for the next UI/runtime step. */
    public static Prefs preferences(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return Prefs.defaults();
        }
        return DanceLibrary.get(player.getServer()).preferences(player.getUUID());
    }

    public static void receive(ServerPlayer player, DancePackets.Request request) {
        if (player == null || request == null || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        State state = state(server);
        switch (request.action()) {
            case LIST -> sendCatalog(player, false);
            case BEGIN_UPLOAD -> beginUpload(player, state, request);
            case END_UPLOAD -> endUpload(player, server, state, request);
            case CANCEL_UPLOAD -> cancelUpload(player, state, request);
            case DOWNLOAD -> beginDownload(player, server, state, request);
            case DELETE -> deleteClip(player, server, request);
            case RENAME -> renameClip(player, server, request);
            default -> reply(player, request.action(), request.ticket(), false,
                "message.kuso_penguin.dance.invalid", request.clipId(), 0);
        }
    }

    public static void receive(ServerPlayer player, DancePackets.Chunk chunk) {
        if (player == null || chunk == null || player.getServer() == null) {
            return;
        }
        State state = state(player.getServer());
        Upload upload = state.uploads.get(player.getUUID());
        if (upload == null) {
            // A stale chunk after cancel/timeout has no state to mutate.
            return;
        }
        byte[] data = chunk.data();
        if (!upload.ticket.equals(chunk.ticket()) || chunk.offset() != upload.received
            || data.length <= 0 || data.length > DancePackets.MAX_CHUNK
            || (long) upload.received + data.length > upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.dance.invalid");
            return;
        }
        if (state.globalChunkTick != state.tick) {
            state.globalChunkTick = state.tick;
            state.globalChunksThisTick = 0;
        }
        if (upload.chunkTick != state.tick) {
            upload.chunkTick = state.tick;
            upload.chunksThisTick = 0;
        }
        if (upload.chunksThisTick >= MAX_CHUNKS_PER_PLAYER_TICK
            || state.globalChunksThisTick >= MAX_CHUNKS_GLOBAL_TICK) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.dance.busy");
            return;
        }

        System.arraycopy(data, 0, upload.buffer, upload.received, data.length);
        upload.received += data.length;
        upload.lastActivityTick = state.tick;
        upload.chunksThisTick++;
        state.globalChunksThisTick++;
    }

    public static void receive(ServerPlayer player, DancePackets.Control control) {
        if (player == null || control == null || player.getServer() == null) {
            return;
        }
        State state = state(player.getServer());
        if (!allowControl(state, player.getUUID())) {
            reply(player, control.action(), control.ticket(), false,
                "message.kuso_penguin.dance.busy", control.clipId(), 0);
            return;
        }
        switch (control.action()) {
            case SET_PREF -> setPreference(player, control);
            case SET_SLOT -> setSlot(player, control);
            case PLAY, STOP -> forwardControl(player, control);
            default -> reply(player, control.action(), control.ticket(), false,
                "message.kuso_penguin.dance.invalid", control.clipId(), 0);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = stateIfPresent(server);
        if (state == null) {
            return;
        }
        state.tick++;
        expireTransfers(server, state);
        acknowledgeUploads(server, state);
        pumpDownloads(server, state);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        State state = stateIfPresent(player.getServer());
        if (state == null) {
            return;
        }
        UUID playerId = player.getUUID();
        Upload upload = state.uploads.remove(playerId);
        if (upload != null) {
            state.transferReserved -= upload.declaredBytes;
            upload.buffer = null;
        }
        Upload pending = state.pendingImports.remove(playerId);
        if (pending != null) {
            state.transferReserved -= pending.declaredBytes;
            pending.cancelled = true;
            pending.buffer = null;
        }
        Download download = state.downloads.remove(playerId);
        if (download != null) {
            state.transferReserved -= download.totalBytes;
            download.buffer = null;
        }
        state.uploadCooldowns.remove(playerId);
        state.controlCooldowns.remove(playerId);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        State state;
        synchronized (DanceServer.class) {
            state = STATES.remove(event.getServer());
        }
        if (state != null) {
            state.stopped = true;
            state.executor.shutdownNow();
            state.uploads.clear();
            state.pendingImports.clear();
            state.downloads.clear();
            state.uploadCooldowns.clear();
            state.controlCooldowns.clear();
            state.transferReserved = 0L;
        }
        DanceLibrary.close(event.getServer());
    }

    private static void beginUpload(ServerPlayer player, State state, DancePackets.Request request) {
        UUID playerId = player.getUUID();
        if (state.tick < state.uploadCooldowns.getOrDefault(playerId, 0L)) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.busy", "", 0);
            return;
        }
        if (state.uploads.containsKey(playerId) || state.pendingImports.containsKey(playerId)
            || state.uploads.size() + state.pendingImports.size() >= MAX_ACTIVE_UPLOADS) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.busy", "", 0);
            return;
        }
        int declared = request.totalBytes();
        if (declared <= 0 || declared > DanceLibrary.MAX_FILE_BYTES) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.invalid", "", 0);
            return;
        }
        if (request.title().isBlank()) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.invalid", "", 0);
            return;
        }
        DanceLibrary library = DanceLibrary.get(player.getServer());
        if (library.ownedCount(playerId) >= DanceLibrary.MAX_CLIPS_PER_PLAYER) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.limit", "", 0);
            return;
        }
        if (declared > DanceLibrary.MAX_BYTES_PER_PLAYER - library.ownedBytes(playerId)) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.storage_full", "", 0);
            return;
        }
        if (declared > DanceLibrary.MAX_GLOBAL_BYTES - state.transferReserved) {
            reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.storage_full", "", 0);
            return;
        }

        // Allocation happens only after ticket/range/per-player/global checks.
        byte[] buffer = new byte[declared];
        Upload upload = new Upload(playerId, player.connection, request.ticket(), request.title(),
            request.expectedHash(), declared, buffer, state.tick);
        state.uploads.put(playerId, upload);
        state.transferReserved += declared;
        state.uploadCooldowns.put(playerId, state.tick + BEGIN_COOLDOWN_TICKS);
        reply(player, DancePackets.Action.BEGIN_UPLOAD, request.ticket(), true, "", "", declared);
    }

    private static void endUpload(ServerPlayer player, MinecraftServer server, State state,
                                  DancePackets.Request request) {
        UUID playerId = player.getUUID();
        Upload upload = state.uploads.get(playerId);
        if (upload == null) {
            reply(player, DancePackets.Action.END_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.no_upload", "", 0);
            return;
        }
        if (!upload.ticket.equals(request.ticket()) || request.totalBytes() != upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.dance.invalid");
            return;
        }
        if (upload.received != upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.dance.incomplete");
            return;
        }

        state.uploads.remove(playerId);
        state.pendingImports.put(playerId, upload);
        byte[] data = upload.buffer;
        upload.buffer = null;
        // Capture the world-scoped library on the server thread so a worker cannot recreate a
        // fresh instance after the server has stopped and the cache was closed.
        DanceLibrary library = DanceLibrary.get(server);
        // The reservation stays until the worker reports back, so queued imports cannot
        // collectively exceed the aggregate cap while their bytes are still in memory.
        try {
            state.executor.execute(() -> {
                DanceLibrary.Clip clip = null;
                Exception failure = null;
                try {
                    if (upload.cancelled || state.stopped) {
                        throw new IOException(state.stopped
                            ? "Server stopped before the import could run"
                            : "Upload was cancelled before the import could run");
                    }
                    clip = library.importClip(upload.playerId, upload.title, data,
                        upload.expectedHash);
                } catch (Exception e) {
                    failure = e;
                }
                DanceLibrary.Clip result = clip;
                Exception error = failure;
                try {
                    server.execute(() -> finishImport(state, server, upload, result, error));
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not schedule dance import result for {}", upload.playerId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            state.pendingImports.remove(playerId, upload);
            state.transferReserved -= upload.declaredBytes;
            reply(player, DancePackets.Action.END_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.busy", "", 0);
        }
    }

    private static void finishImport(State state, MinecraftServer server, Upload upload,
                                     DanceLibrary.Clip clip, Exception failure) {
        if (state.stopped) {
            return;
        }
        if (state.pendingImports.get(upload.playerId) != upload) {
            // Cancelled, logged out or stopped; the reservation was already released there.
            return;
        }
        state.pendingImports.remove(upload.playerId);
        state.transferReserved -= upload.declaredBytes;
        ServerPlayer player = sameConnectionPlayer(server, upload.playerId, upload.connection);
        if (player == null) {
            return;
        }
        if (failure != null || clip == null) {
            LOGGER.warn("Dance import failed for {}: {}", upload.playerId,
                failure == null ? "unknown error" : failure.getMessage());
            reply(player, DancePackets.Action.END_UPLOAD, upload.ticket, false,
                "message.kuso_penguin.dance.import_failed", "", 0);
            player.sendSystemMessage(Component.translatableWithFallback(
                "message.kuso_penguin.dance.import_failed.detail",
                "Dance import failed: %s", failureDetail(failure)));
            return;
        }
        reply(player, DancePackets.Action.END_UPLOAD, upload.ticket, true, "", clip.hash(),
            clip.bytes());
        sendCatalog(player, false);
    }

    /** Bounded, one-line detail for the generic reply plus client system message. */
    private static String failureDetail(Exception failure) {
        String raw = failure == null ? null : failure.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = failure == null ? "unknown error" : failure.getClass().getSimpleName();
        }
        StringBuilder bounded = new StringBuilder(MAX_ERROR_DETAIL_CHARS);
        for (int i = 0; i < raw.length() && bounded.length() < MAX_ERROR_DETAIL_CHARS; i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                if (bounded.length() > 0 && bounded.charAt(bounded.length() - 1) != ' ') {
                    bounded.append(' ');
                }
            } else {
                bounded.append(c);
            }
        }
        String detail = bounded.toString().strip();
        return detail.isEmpty() ? "unknown error" : detail;
    }

    private static void cancelUpload(ServerPlayer player, State state, DancePackets.Request request) {
        UUID playerId = player.getUUID();
        Upload upload = state.uploads.get(playerId);
        if (upload != null && upload.ticket.equals(request.ticket())) {
            state.uploads.remove(playerId);
            state.transferReserved -= upload.declaredBytes;
            upload.cancelled = true;
            upload.buffer = null;
            reply(player, DancePackets.Action.CANCEL_UPLOAD, request.ticket(), true, "", "", 0);
            return;
        }
        Upload pending = state.pendingImports.get(playerId);
        if (pending != null && pending.ticket.equals(request.ticket())) {
            state.pendingImports.remove(playerId);
            state.transferReserved -= pending.declaredBytes;
            pending.cancelled = true;
            pending.buffer = null;
            reply(player, DancePackets.Action.CANCEL_UPLOAD, request.ticket(), true, "", "", 0);
            return;
        }
        reply(player, DancePackets.Action.CANCEL_UPLOAD, request.ticket(), false,
            "message.kuso_penguin.dance.no_upload", "", 0);
    }

    private static void beginDownload(ServerPlayer player, MinecraftServer server, State state,
                                      DancePackets.Request request) {
        DanceLibrary.Clip clip = DanceLibrary.get(server).get(request.clipId());
        if (clip == null) {
            reply(player, DancePackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.unknown_clip", "", 0);
            return;
        }
        UUID playerId = player.getUUID();
        if (state.downloads.containsKey(playerId) || state.downloads.size() >= MAX_ACTIVE_DOWNLOADS) {
            reply(player, DancePackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.busy", "", 0);
            return;
        }
        if (clip.bytes() > DanceLibrary.MAX_GLOBAL_BYTES - state.transferReserved) {
            reply(player, DancePackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.storage_full", "", 0);
            return;
        }

        Download download = new Download(playerId, player.connection, request.ticket(), clip.hash(),
            clip.bytes(), state.tick);
        state.downloads.put(playerId, download);
        state.transferReserved += clip.bytes();
        DanceLibrary library = DanceLibrary.get(server);
        try {
            state.executor.execute(() -> {
                byte[] data = null;
                Exception failure = null;
                try {
                    data = library.read(download.clipHash);
                } catch (Exception e) {
                    failure = e;
                }
                byte[] result = data;
                Exception error = failure;
                try {
                    server.execute(() -> finishDownload(state, server, download, result, error));
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not schedule dance download result for {}", download.playerId,
                        e);
                }
            });
        } catch (RejectedExecutionException e) {
            state.downloads.remove(playerId);
            state.transferReserved -= download.totalBytes;
            reply(player, DancePackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.dance.busy", "", 0);
        }
    }

    private static void finishDownload(State state, MinecraftServer server, Download download,
                                       byte[] data, Exception failure) {
        if (state.stopped || state.downloads.get(download.playerId) != download) {
            return;
        }
        ServerPlayer player = sameConnectionPlayer(server, download.playerId, download.connection);
        if (player == null) {
            state.downloads.remove(download.playerId);
            state.transferReserved -= download.totalBytes;
            return;
        }
        if (failure != null || data == null || data.length != download.totalBytes) {
            state.downloads.remove(download.playerId);
            state.transferReserved -= download.totalBytes;
            LOGGER.warn("Dance download failed for {}: {}", download.playerId,
                failure == null ? "size mismatch" : failure.getMessage());
            reply(player, DancePackets.Action.DOWNLOAD, download.ticket, false,
                "message.kuso_penguin.dance.import_failed", "", 0);
            return;
        }
        download.buffer = data;
        reply(player, DancePackets.Action.DOWNLOAD, download.ticket, true,
            "message.kuso_penguin.dance.download_ready", download.clipHash, download.totalBytes);
    }

    private static void deleteClip(ServerPlayer player, MinecraftServer server,
                                   DancePackets.Request request) {
        boolean operator = player.hasPermissions(2);
        try {
            boolean deleted = DanceLibrary.get(server).delete(request.clipId(), player.getUUID(),
                operator);
            reply(player, DancePackets.Action.DELETE, request.ticket(), deleted,
                deleted ? "" : "message.kuso_penguin.dance.unknown_clip", request.clipId(), 0);
            if (deleted) {
                sendCatalog(player, false);
            }
        } catch (IOException e) {
            LOGGER.warn("Dance delete failed for {}: {}", player.getUUID(), e.getMessage());
            reply(player, DancePackets.Action.DELETE, request.ticket(), false,
                "message.kuso_penguin.dance.not_owner", request.clipId(), 0);
        }
    }

    private static void renameClip(ServerPlayer player, MinecraftServer server,
                                   DancePackets.Request request) {
        boolean operator = player.hasPermissions(2);
        try {
            DanceLibrary.Clip renamed = DanceLibrary.get(server).rename(request.clipId(),
                player.getUUID(), operator, request.title());
            reply(player, DancePackets.Action.RENAME, request.ticket(), true, "", renamed.hash(),
                renamed.bytes());
            sendCatalog(player, false);
        } catch (IOException e) {
            LOGGER.warn("Dance rename failed for {}: {}", player.getUUID(), e.getMessage());
            reply(player, DancePackets.Action.RENAME, request.ticket(), false,
                "message.kuso_penguin.dance.not_owner", request.clipId(), 0);
        }
    }

    private static void setPreference(ServerPlayer player, DancePackets.Control control) {
        if (!control.target().equals(player.getUUID())) {
            reply(player, DancePackets.Action.SET_PREF, control.ticket(), false,
                "message.kuso_penguin.dance.invalid", control.clipId(), 0);
            return;
        }
        if (!player.hasEffect(ModEffects.PENGUIN)) {
            reply(player, DancePackets.Action.SET_PREF, control.ticket(), false,
                "message.kuso_penguin.dance.effect_required", control.clipId(), 0);
            return;
        }
        try {
            Prefs prefs = DanceLibrary.get(player.getServer()).setPreference(player.getUUID(),
                control.trigger(), control.clipId());
            reply(player, DancePackets.Action.SET_PREF, control.ticket(), true, "",
                selected(prefs, control.trigger()), 0);
            sendCatalog(player, false);
        } catch (IOException e) {
            LOGGER.warn("Dance preference update failed for {}: {}", player.getUUID(),
                e.getMessage());
            reply(player, DancePackets.Action.SET_PREF, control.ticket(), false,
                "message.kuso_penguin.dance.invalid", control.clipId(), 0);
        }
    }

    private static void setSlot(ServerPlayer player, DancePackets.Control control) {
        if (!control.target().equals(player.getUUID())) {
            reply(player, DancePackets.Action.SET_SLOT, control.ticket(), false,
                "message.kuso_penguin.dance.invalid", control.clipId(), 0);
            return;
        }
        if (!player.hasEffect(ModEffects.PENGUIN)) {
            reply(player, DancePackets.Action.SET_SLOT, control.ticket(), false,
                "message.kuso_penguin.dance.effect_required", control.clipId(), 0);
            return;
        }
        try {
            Prefs prefs = DanceLibrary.get(player.getServer()).setSlot(player.getUUID(),
                control.slot(), control.clipId());
            reply(player, DancePackets.Action.SET_SLOT, control.ticket(), true, "",
                prefs.slots().get(control.slot()), 0);
            sendCatalog(player, false);
        } catch (IOException e) {
            LOGGER.warn("Dance slot update failed for {}: {}", player.getUUID(), e.getMessage());
            reply(player, DancePackets.Action.SET_SLOT, control.ticket(), false,
                "message.kuso_penguin.dance.invalid", control.clipId(), 0);
        }
    }

    private static String selected(Prefs prefs, Trigger trigger) {
        return switch (trigger) {
            case MUSIC -> prefs.music();
            case REVIVAL -> prefs.revival();
            case MAID_MUSIC -> prefs.maidMusic();
        };
    }

    private static void forwardControl(ServerPlayer player, DancePackets.Control control) {
        if (control.action() == DancePackets.Action.PLAY
            || control.action() == DancePackets.Action.STOP) {
            // A STOP is always owner-checked by the installed actor manager even after the sender
            // lost the effect or the clip registration, so only PLAY needs the selection check.
            if (control.action() == DancePackets.Action.PLAY && !control.clipId().isEmpty()
                && !DanceLibrary.get(player.getServer()).selectable(player.getUUID(),
                    control.clipId())) {
                reply(player, control.action(), control.ticket(), false,
                    "message.kuso_penguin.dance.not_owner", control.clipId(), 0);
                return;
            }
            if (control.action() == DancePackets.Action.PLAY
                && !player.hasEffect(ModEffects.PENGUIN)) {
                reply(player, control.action(), control.ticket(), false,
                    "message.kuso_penguin.dance.effect_required", control.clipId(), 0);
                return;
            }
        }
        try {
            controlHandler.accept(player, control);
        } catch (RuntimeException e) {
            LOGGER.warn("Installed dance control handler failed for {}", player.getUUID(), e);
            reply(player, control.action(), control.ticket(), false,
                "message.kuso_penguin.dance.unimplemented", control.clipId(), 0);
        }
    }

    private static void expireTransfers(MinecraftServer server, State state) {
        Iterator<Map.Entry<UUID, Upload>> uploads = state.uploads.entrySet().iterator();
        while (uploads.hasNext()) {
            Upload upload = uploads.next().getValue();
            if (state.tick - upload.lastActivityTick <= IDLE_TIMEOUT_TICKS) {
                continue;
            }
            uploads.remove();
            state.transferReserved -= upload.declaredBytes;
            upload.buffer = null;
            ServerPlayer player = sameConnectionPlayer(server, upload.playerId, upload.connection);
            if (player != null) {
                reply(player, DancePackets.Action.END_UPLOAD, upload.ticket, false,
                    "message.kuso_penguin.dance.timeout", "", 0);
            }
        }
        Iterator<Map.Entry<UUID, Download>> downloads = state.downloads.entrySet().iterator();
        while (downloads.hasNext()) {
            Download download = downloads.next().getValue();
            if (state.tick - download.lastActivityTick <= IDLE_TIMEOUT_TICKS) {
                continue;
            }
            downloads.remove();
            state.transferReserved -= download.totalBytes;
            download.buffer = null;
            ServerPlayer player = sameConnectionPlayer(server, download.playerId,
                download.connection);
            if (player != null) {
                reply(player, DancePackets.Action.DOWNLOAD, download.ticket, false,
                    "message.kuso_penguin.dance.timeout", "", 0);
            }
        }
    }

    private static void acknowledgeUploads(MinecraftServer server, State state) {
        for (Upload upload : state.uploads.values()) {
            if (upload.received <= upload.acknowledged) {
                continue;
            }
            ServerPlayer player = sameConnectionPlayer(server, upload.playerId, upload.connection);
            if (player == null) {
                continue;
            }
            upload.acknowledged = upload.received;
            // One credit ACK per upload per tick; the next chunks become legal only next tick.
            reply(player, DancePackets.Action.BEGIN_UPLOAD, upload.ticket, true, PROGRESS_MARKER,
                "", upload.received);
        }
    }

    private static void pumpDownloads(MinecraftServer server, State state) {
        int globalSent = 0;
        Iterator<Map.Entry<UUID, Download>> iterator = state.downloads.entrySet().iterator();
        while (iterator.hasNext() && globalSent < MAX_CHUNKS_GLOBAL_TICK) {
            Download download = iterator.next().getValue();
            if (download.buffer == null) {
                continue;
            }
            ServerPlayer player = sameConnectionPlayer(server, download.playerId,
                download.connection);
            if (player == null) {
                iterator.remove();
                state.transferReserved -= download.totalBytes;
                download.buffer = null;
                continue;
            }
            int sentForPlayer = 0;
            while (download.offset < download.buffer.length
                && sentForPlayer < MAX_CHUNKS_PER_PLAYER_TICK
                && globalSent < MAX_CHUNKS_GLOBAL_TICK) {
                int length = Math.min(DancePackets.MAX_CHUNK,
                    download.buffer.length - download.offset);
                byte[] part = Arrays.copyOfRange(download.buffer, download.offset,
                    download.offset + length);
                PacketDistributor.sendToPlayer(player,
                    new DancePackets.Chunk(download.ticket, download.offset, part));
                download.offset += length;
                download.lastActivityTick = state.tick;
                sentForPlayer++;
                globalSent++;
            }
            if (download.offset >= download.buffer.length) {
                iterator.remove();
                state.transferReserved -= download.totalBytes;
                download.buffer = null;
            }
        }
    }

    private static void cancelAndReply(ServerPlayer player, State state, Upload upload,
                                       String messageKey) {
        if (state.uploads.remove(upload.playerId, upload)) {
            state.transferReserved -= upload.declaredBytes;
            upload.buffer = null;
        }
        reply(player, DancePackets.Action.END_UPLOAD, upload.ticket, false, messageKey, "", 0);
    }

    private static ServerPlayer sameConnectionPlayer(MinecraftServer server, UUID playerId,
                                                     ServerGamePacketListenerImpl connection) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.connection == null || player.connection != connection
            || player.isRemoved()) {
            return null;
        }
        return player;
    }

    private static void reply(ServerPlayer player, DancePackets.Action action, UUID ticket,
                              boolean success, String message, String clipId, int totalBytes) {
        PacketDistributor.sendToPlayer(player,
            new DancePackets.Reply(action, ticket, success, message, clipId, totalBytes));
    }

    private static boolean allowControl(State state, UUID playerId) {
        Long last = state.controlCooldowns.get(playerId);
        if (last != null && state.tick - last < CONTROL_COOLDOWN_TICKS) {
            return false;
        }
        state.controlCooldowns.put(playerId, state.tick);
        return true;
    }

    private static State state(MinecraftServer server) {
        synchronized (DanceServer.class) {
            return STATES.computeIfAbsent(server, ignored -> new State());
        }
    }

    private static State stateIfPresent(MinecraftServer server) {
        synchronized (DanceServer.class) {
            return STATES.get(server);
        }
    }

    private static final class State {
        private final ThreadPoolExecutor executor;
        private final Map<UUID, Upload> uploads = new HashMap<>();
        private final Map<UUID, Upload> pendingImports = new HashMap<>();
        private final Map<UUID, Download> downloads = new HashMap<>();
        private final Map<UUID, Long> uploadCooldowns = new HashMap<>();
        private final Map<UUID, Long> controlCooldowns = new HashMap<>();
        private long tick;
        private long transferReserved;
        private long globalChunkTick = Long.MIN_VALUE;
        private int globalChunksThisTick;
        private volatile boolean stopped;

        private State() {
            AtomicInteger threadNumber = new AtomicInteger();
            this.executor = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 30L,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(WORK_QUEUE), runnable -> {
                    Thread thread = new Thread(runnable,
                        "KusoPenguin-Dance-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
            this.executor.allowCoreThreadTimeOut(true);
        }
    }

    private static final class Upload {
        private final UUID playerId;
        private final ServerGamePacketListenerImpl connection;
        private final UUID ticket;
        private final String title;
        private final String expectedHash;
        private final int declaredBytes;
        private byte[] buffer;
        private int received;
        private int acknowledged;
        private long lastActivityTick;
        private long chunkTick = Long.MIN_VALUE;
        private int chunksThisTick;
        private volatile boolean cancelled;

        private Upload(UUID playerId, ServerGamePacketListenerImpl connection, UUID ticket,
                       String title, String expectedHash, int declaredBytes, byte[] buffer,
                       long tick) {
            this.playerId = playerId;
            this.connection = connection;
            this.ticket = ticket;
            this.title = title;
            this.expectedHash = expectedHash;
            this.declaredBytes = declaredBytes;
            this.buffer = buffer;
            this.lastActivityTick = tick;
        }
    }

    private static final class Download {
        private final UUID playerId;
        private final ServerGamePacketListenerImpl connection;
        private final UUID ticket;
        private final String clipHash;
        private final int totalBytes;
        private long lastActivityTick;
        private byte[] buffer;
        private int offset;

        private Download(UUID playerId, ServerGamePacketListenerImpl connection, UUID ticket,
                         String clipHash, int totalBytes, long tick) {
            this.playerId = playerId;
            this.connection = connection;
            this.ticket = ticket;
            this.clipHash = clipHash;
            this.totalBytes = totalBytes;
            this.lastActivityTick = tick;
        }
    }
}
