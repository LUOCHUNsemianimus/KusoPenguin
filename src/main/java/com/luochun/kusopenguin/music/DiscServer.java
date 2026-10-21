package com.luochun.kusopenguin.music;

import com.luochun.kusopenguin.KusoPenguin;
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
 * Server-side upload/download state machine for custom discs.
 *
 * <p>All maps and reservations live on the server thread. Disk work runs on a bounded two-thread
 * executor and returns through {@link MinecraftServer#execute(Runnable)}; a worker never mutates
 * the transfer maps directly. Each upload/download owns one player and one ticket, and every
 * buffer is reserved only after the corresponding quota check. Stale player state disappears on
 * logout and the whole state disappears on server stop.</p>
 */
@EventBusSubscriber(modid = KusoPenguin.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DiscServer {
    private static final int CANONICAL_HEADER_BYTES = 44;
    private static final long MAX_RESERVED_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ACTIVE_UPLOADS = 4;
    private static final int MAX_ACTIVE_DOWNLOADS = 4;
    private static final int MAX_CHUNKS_PER_PLAYER_TICK = 4;
    private static final int MAX_CHUNKS_GLOBAL_TICK = 16;
    private static final int IDLE_TIMEOUT_TICKS = 1200;
    private static final int BEGIN_COOLDOWN_TICKS = 40;
    private static final int WORKER_THREADS = 2;
    private static final int WORK_QUEUE = 8;
    /** Private client-control marker: a new upload credit window is available. */
    private static final String PROGRESS_MARKER = "progress";

    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/DiscServer");

    /** Weak keys: a state must never keep a Minecraft server alive. */
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private DiscServer() {
    }

    /** Open the catalog screen through the later client listener. */
    public static void open(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        sendCatalog(player, true);
    }

    public static void receive(ServerPlayer player, DiscPackets.Request request) {
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
            case SELECT -> selectTrack(player, server, request);
            case DOWNLOAD -> beginDownload(player, server, state, request);
        }
    }

    public static void receive(ServerPlayer player, DiscPackets.Chunk chunk) {
        if (player == null || chunk == null || player.getServer() == null) {
            return;
        }
        State state = state(player.getServer());
        UUID playerId = player.getUUID();
        Upload upload = state.uploads.get(playerId);
        if (upload == null) {
            // Stale chunks from an already-cancelled/timed-out upload are ignored silently.
            return;
        }
        byte[] data = chunk.data();
        if (!upload.ticket.equals(chunk.ticket()) || chunk.offset() != upload.received
            || data.length <= 0 || data.length > WaveFile.MAX_CHUNK
            || (long) upload.received + data.length > upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.disc.invalid");
            return;
        }
        if (upload.chunkTick != state.tick) {
            upload.chunkTick = state.tick;
            upload.chunksThisTick = 0;
        }
        if (upload.chunksThisTick >= MAX_CHUNKS_PER_PLAYER_TICK) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.disc.busy");
            return;
        }

        System.arraycopy(data, 0, upload.buffer, upload.received, data.length);
        upload.received += data.length;
        upload.lastActivityTick = state.tick;
        upload.chunksThisTick++;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = stateIfPresent(server);
        if (state == null) {
            return;
        }
        state.tick++;
        expireUploads(server, state);
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
            state.uploadReserved -= upload.declaredBytes;
            upload.buffer = null;
        }
        Download download = state.downloads.remove(playerId);
        if (download != null) {
            state.downloadReserved -= download.totalBytes;
            download.buffer = null;
        }
        state.uploadCooldowns.remove(playerId);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        State state;
        synchronized (DiscServer.class) {
            state = STATES.remove(event.getServer());
        }
        if (state != null) {
            state.stopped = true;
            state.executor.shutdownNow();
            state.uploads.clear();
            state.downloads.clear();
            state.uploadCooldowns.clear();
        }
        MusicLibrary.close(event.getServer());
    }

    private static void beginUpload(ServerPlayer player, State state, DiscPackets.Request request) {
        UUID playerId = player.getUUID();
        if (state.tick < state.uploadCooldowns.getOrDefault(playerId, 0L)) {
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.busy", "", 0);
            return;
        }
        if (state.uploads.containsKey(playerId) || state.uploads.size() >= MAX_ACTIVE_UPLOADS) {
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.busy", "", 0);
            return;
        }
        int declared = request.totalBytes();
        if (declared < CANONICAL_HEADER_BYTES || declared > WaveFile.MAX_BYTES) {
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.invalid", "", 0);
            return;
        }
        if (request.title().isBlank()) {
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.invalid", "", 0);
            return;
        }
        if (state.uploadReserved + declared > MAX_RESERVED_BYTES) {
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.storage_full", "", 0);
            return;
        }

        // Allocation happens only after ticket/range/global/quota validation.
        byte[] buffer = new byte[declared];
        Upload upload = new Upload(playerId, player.connection, request.ticket(), request.title(),
            declared, buffer, state.tick);
        state.uploads.put(playerId, upload);
        state.uploadReserved += declared;
        state.uploadCooldowns.put(playerId, state.tick + BEGIN_COOLDOWN_TICKS);
        reply(player, DiscPackets.Action.BEGIN_UPLOAD, request.ticket(), true, "", "", declared);
    }

    private static void endUpload(ServerPlayer player, MinecraftServer server, State state,
                                  DiscPackets.Request request) {
        UUID playerId = player.getUUID();
        Upload upload = state.uploads.get(playerId);
        if (upload == null) {
            reply(player, DiscPackets.Action.END_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.no_upload", "", 0);
            return;
        }
        if (!upload.ticket.equals(request.ticket()) || request.totalBytes() != upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.disc.invalid");
            return;
        }
        if (upload.received != upload.declaredBytes) {
            cancelAndReply(player, state, upload, "message.kuso_penguin.disc.incomplete");
            return;
        }

        state.uploads.remove(playerId);
        byte[] data = upload.buffer;
        upload.buffer = null;
        // The reservation stays until the worker reports back, so concurrent imports cannot
        // collectively exceed the aggregate cap while these bytes are still queued.
        try {
            state.executor.execute(() -> {
                MusicLibrary.Track track = null;
                Exception failure = null;
                try {
                    track = MusicLibrary.get(server).importTrack(upload.playerId, upload.title, data);
                } catch (Exception e) {
                    failure = e;
                }
                MusicLibrary.Track result = track;
                Exception error = failure;
                try {
                    server.execute(() -> finishImport(state, server, upload, result, error));
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not schedule disc import result for {}", upload.playerId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            state.uploadReserved -= upload.declaredBytes;
            reply(player, DiscPackets.Action.END_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.busy", "", 0);
        }
    }

    private static void finishImport(State state, MinecraftServer server, Upload upload,
                                     MusicLibrary.Track track, Exception failure) {
        state.uploadReserved -= upload.declaredBytes;
        if (state.stopped) {
            return;
        }
        ServerPlayer player = sameConnectionPlayer(server, upload.playerId, upload.connection);
        if (player == null) {
            return;
        }
        if (failure != null || track == null) {
            LOGGER.warn("Disc import failed for {}: {}", upload.playerId,
                failure == null ? "unknown error" : failure.getMessage());
            reply(player, DiscPackets.Action.END_UPLOAD, upload.ticket, false,
                "message.kuso_penguin.disc.import_failed", "", 0);
            return;
        }
        reply(player, DiscPackets.Action.END_UPLOAD, upload.ticket, true, "",
            track.id(), track.totalBytes());
        sendCatalog(player, false);
    }

    private static void cancelUpload(ServerPlayer player, State state, DiscPackets.Request request) {
        Upload upload = state.uploads.get(player.getUUID());
        if (upload == null || !upload.ticket.equals(request.ticket())) {
            reply(player, DiscPackets.Action.CANCEL_UPLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.no_upload", "", 0);
            return;
        }
        state.uploads.remove(player.getUUID());
        state.uploadReserved -= upload.declaredBytes;
        upload.buffer = null;
        reply(player, DiscPackets.Action.CANCEL_UPLOAD, request.ticket(), true, "", "", 0);
    }

    private static void selectTrack(ServerPlayer player, MinecraftServer server,
                                    DiscPackets.Request request) {
        MusicLibrary.Track track = MusicLibrary.get(server).get(request.trackId());
        if (track == null) {
            reply(player, DiscPackets.Action.SELECT, request.ticket(), false,
                "message.kuso_penguin.disc.unknown_track", "", 0);
            return;
        }
        MusicItems.give(player, track);
        reply(player, DiscPackets.Action.SELECT, request.ticket(), true, "",
            track.id(), track.totalBytes());
    }

    private static void beginDownload(ServerPlayer player, MinecraftServer server, State state,
                                      DiscPackets.Request request) {
        MusicLibrary.Track track = MusicLibrary.get(server).get(request.trackId());
        if (track == null) {
            reply(player, DiscPackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.unknown_track", "", 0);
            return;
        }
        UUID playerId = player.getUUID();
        if (state.downloads.containsKey(playerId) || state.downloads.size() >= MAX_ACTIVE_DOWNLOADS) {
            reply(player, DiscPackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.busy", "", 0);
            return;
        }
        if (state.downloadReserved + track.totalBytes() > MAX_RESERVED_BYTES) {
            reply(player, DiscPackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.storage_full", "", 0);
            return;
        }

        Download download = new Download(playerId, player.connection, request.ticket(), track.id(),
            track.totalBytes());
        state.downloads.put(playerId, download);
        state.downloadReserved += track.totalBytes();
        try {
            state.executor.execute(() -> {
                byte[] data = null;
                Exception failure = null;
                try {
                    data = MusicLibrary.get(server).read(download.trackId);
                } catch (Exception e) {
                    failure = e;
                }
                byte[] result = data;
                Exception error = failure;
                try {
                    server.execute(() -> finishDownload(state, server, download, result, error));
                } catch (RuntimeException e) {
                    LOGGER.warn("Could not schedule disc download result for {}", download.playerId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            state.downloads.remove(playerId);
            state.downloadReserved -= download.totalBytes;
            reply(player, DiscPackets.Action.DOWNLOAD, request.ticket(), false,
                "message.kuso_penguin.disc.busy", "", 0);
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
            state.downloadReserved -= download.totalBytes;
            return;
        }
        if (failure != null || data == null || data.length != download.totalBytes) {
            state.downloads.remove(download.playerId);
            state.downloadReserved -= download.totalBytes;
            reply(player, DiscPackets.Action.DOWNLOAD, download.ticket, false,
                "message.kuso_penguin.disc.import_failed", "", 0);
            return;
        }
        download.buffer = data;
        reply(player, DiscPackets.Action.DOWNLOAD, download.ticket, true,
            "message.kuso_penguin.disc.download_ready", download.trackId, download.totalBytes);
    }

    private static void expireUploads(MinecraftServer server, State state) {
        Iterator<Map.Entry<UUID, Upload>> iterator = state.uploads.entrySet().iterator();
        while (iterator.hasNext()) {
            Upload upload = iterator.next().getValue();
            if (state.tick - upload.lastActivityTick <= IDLE_TIMEOUT_TICKS) {
                continue;
            }
            iterator.remove();
            state.uploadReserved -= upload.declaredBytes;
            upload.buffer = null;
            ServerPlayer player = sameConnectionPlayer(server, upload.playerId, upload.connection);
            if (player != null) {
                reply(player, DiscPackets.Action.END_UPLOAD, upload.ticket, false,
                    "message.kuso_penguin.disc.timeout", "", 0);
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
            // At most one credit ACK per upload per server tick; this grants the next four
            // chunks only after a new tick, never as an immediate per-chunk acknowledgement.
            reply(player, DiscPackets.Action.BEGIN_UPLOAD, upload.ticket, true, PROGRESS_MARKER,
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
            ServerPlayer player = sameConnectionPlayer(server, download.playerId, download.connection);
            if (player == null) {
                iterator.remove();
                state.downloadReserved -= download.totalBytes;
                download.buffer = null;
                continue;
            }
            int sentForPlayer = 0;
            while (download.offset < download.buffer.length
                && sentForPlayer < MAX_CHUNKS_PER_PLAYER_TICK
                && globalSent < MAX_CHUNKS_GLOBAL_TICK) {
                int length = Math.min(WaveFile.MAX_CHUNK, download.buffer.length - download.offset);
                byte[] part = Arrays.copyOfRange(download.buffer, download.offset,
                    download.offset + length);
                PacketDistributor.sendToPlayer(player,
                    new DiscPackets.Chunk(download.ticket, download.offset, part));
                download.offset += length;
                sentForPlayer++;
                globalSent++;
            }
            if (download.offset >= download.buffer.length) {
                iterator.remove();
                state.downloadReserved -= download.totalBytes;
                // The buffer (and its reservation) is released only after the final chunk.
                download.buffer = null;
            }
        }
    }

    private static void cancelAndReply(ServerPlayer player, State state, Upload upload,
                                       String messageKey) {
        if (state.uploads.remove(upload.playerId, upload)) {
            state.uploadReserved -= upload.declaredBytes;
            upload.buffer = null;
        }
        reply(player, DiscPackets.Action.END_UPLOAD, upload.ticket, false, messageKey, "", 0);
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

    private static void sendCatalog(ServerPlayer player, boolean open) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
            new DiscPackets.Catalog(open, MusicLibrary.get(server).catalog()));
    }

    private static void reply(ServerPlayer player, DiscPackets.Action action, UUID ticket,
                              boolean success, String message, String trackId, int totalBytes) {
        PacketDistributor.sendToPlayer(player,
            new DiscPackets.Reply(action, ticket, success, message, trackId, totalBytes));
    }

    private static State state(MinecraftServer server) {
        synchronized (DiscServer.class) {
            return STATES.computeIfAbsent(server, ignored -> new State());
        }
    }

    private static State stateIfPresent(MinecraftServer server) {
        synchronized (DiscServer.class) {
            return STATES.get(server);
        }
    }

    private static final class State {
        private final ThreadPoolExecutor executor;
        private final Map<UUID, Upload> uploads = new HashMap<>();
        private final Map<UUID, Download> downloads = new HashMap<>();
        private final Map<UUID, Long> uploadCooldowns = new HashMap<>();
        private long tick;
        private long uploadReserved;
        private long downloadReserved;
        private boolean stopped;

        private State() {
            AtomicInteger threadNumber = new AtomicInteger();
            this.executor = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 30L,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(WORK_QUEUE), runnable -> {
                    Thread thread = new Thread(runnable,
                        "KusoPenguin-Disc-" + threadNumber.incrementAndGet());
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
        private final int declaredBytes;
        private byte[] buffer;
        private int received;
        private int acknowledged;
        private long lastActivityTick;
        private long chunkTick = Long.MIN_VALUE;
        private int chunksThisTick;

        private Upload(UUID playerId, ServerGamePacketListenerImpl connection, UUID ticket,
                       String title, int declaredBytes, byte[] buffer, long tick) {
            this.playerId = playerId;
            this.connection = connection;
            this.ticket = ticket;
            this.title = title;
            this.declaredBytes = declaredBytes;
            this.buffer = buffer;
            this.lastActivityTick = tick;
        }
    }

    private static final class Download {
        private final UUID playerId;
        private final ServerGamePacketListenerImpl connection;
        private final UUID ticket;
        private final String trackId;
        private final int totalBytes;
        private byte[] buffer;
        private int offset;

        private Download(UUID playerId, ServerGamePacketListenerImpl connection, UUID ticket,
                         String trackId, int totalBytes) {
            this.playerId = playerId;
            this.connection = connection;
            this.ticket = ticket;
            this.trackId = trackId;
            this.totalBytes = totalBytes;
        }
    }
}
