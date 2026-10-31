package com.luochun.kusopenguin.client.dance;

import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.dance.DanceImport;
import com.luochun.kusopenguin.dance.DanceLibrary;
import com.luochun.kusopenguin.dance.DancePackets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only, bounded transfer state. Filesystem conversion and JSON parsing happen only on the
 * two IO workers; the client thread only copies bounded chunk slices and publishes immutable
 * {@link PenguinAnimationClip} instances.
 */
public final class DanceTransfers {
    public static final int MAX_CACHE_ENTRIES = 8;
    public static final long MAX_CACHE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_PENDING_DOWNLOADS = 4;
    public static final int MAX_CONTROL_QUEUE = 64;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ExecutorService IO = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "KusoPenguin-DanceIO");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    private static long connectionGeneration;
    private static long importGeneration;
    private static Future<?> importJob;
    private static Upload upload;
    private static DanceImport.Result prepared;
    private static long preparedGeneration;
    private static Component message = Component.empty();

    private static final LinkedHashMap<String, Cached> CACHE =
        new LinkedHashMap<>(16, 0.75F, true);
    private static long cacheBytes;
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static Pending activeDownload;
    private static final Deque<DancePackets.Control> CONTROLS = new ArrayDeque<>();
    private static long nextControlTick;

    private record Cached(DanceLibrary.Canonical canonical, PenguinAnimationClip clip, int bytes) {
    }

    private static final class Upload {
        private final UUID ticket = UUID.randomUUID();
        private final byte[] bytes;
        private final String title;
        private final String expectedHash;
        private int sent;
        private int ack;
        private int limit;
        private boolean ended;

        private Upload(byte[] bytes, String title, String expectedHash) {
            this.bytes = bytes;
            this.title = title;
            this.expectedHash = expectedHash;
        }
    }

    private static final class Pending {
        private final String id;
        private final UUID ticket = UUID.randomUUID();
        private final CompletableFuture<PenguinAnimationClip> result = new CompletableFuture<>();
        private byte[] bytes;
        private int received;
        private int attempts;
        private long retryAt;
        private long started;
        private boolean requested;
        private boolean waiting;

        private Pending(String id) {
            this.id = id;
        }
    }

    private DanceTransfers() {
    }

    public static long preparedGeneration() {
        return preparedGeneration;
    }

    public static DanceImport.Result prepared() {
        return prepared;
    }

    public static Component status() {
        return message;
    }

    public static boolean importing() {
        return importJob != null;
    }

    public static boolean uploading() {
        return upload != null;
    }

    public static boolean previewReady() {
        return prepared != null;
    }

    private static long now() {
        return MC.level == null ? 0L : MC.level.getGameTime();
    }

    private static void send(DancePackets.Action action, UUID ticket, String clipId, String title,
                             int totalBytes, String expectedHash) {
        if (MC.getConnection() != null) {
            PacketDistributor.sendToServer(new DancePackets.Request(action, ticket, clipId, title,
                totalBytes, expectedHash));
        }
    }

    private static void send(DancePackets.Control control) {
        if (MC.getConnection() != null) {
            PacketDistributor.sendToServer(control);
        }
    }

    private static void error(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String detail = Objects.toString(cause.getMessage(), cause.getClass().getSimpleName());
        message = Component.translatable("gui.kuso_penguin.dance.error", detail);
    }

    public static void reportError(Throwable throwable) {
        error(throwable);
    }

    /** Prepare canonical bytes on a worker; no upload starts until the user explicitly chooses it. */
    public static void prepare(Path file) {
        cancelUpload();
        prepared = null;
        preparedGeneration++;
        long generation = ++importGeneration;
        long connection = connectionGeneration;
        Object network = MC.getConnection();
        message = Component.translatable("gui.kuso_penguin.dance.importing");
        try {
            importJob = IO.submit(() -> {
                try {
                    DanceImport.Result result = DanceImport.convert(file);
                    MC.execute(() -> {
                        if (generation != importGeneration || connection != connectionGeneration
                            || network != MC.getConnection()) {
                            return;
                        }
                        importJob = null;
                        prepared = result;
                        preparedGeneration++;
                        message = Component.translatable("gui.kuso_penguin.dance.imported",
                            result.format(), String.format("%.2f", result.duration()),
                            result.frames(), result.rootMotionRemoved()
                                ? Component.translatable("gui.kuso_penguin.dance.removed")
                                : Component.translatable("gui.kuso_penguin.dance.kept"));
                    });
                } catch (Exception exception) {
                    MC.execute(() -> {
                        if (generation == importGeneration && connection == connectionGeneration) {
                            importJob = null;
                            error(exception);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException exception) {
            importJob = null;
            error(exception);
        }
    }

    /** Upload the already validated canonical bytes with the server's byte-credit protocol. */
    public static void upload(String title) {
        if (importJob != null) {
            message = Component.translatable("gui.kuso_penguin.dance.importing");
            return;
        }
        if (prepared == null) {
            message = Component.translatable("gui.kuso_penguin.dance.no_import");
            return;
        }
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty()) {
            message = Component.translatable("gui.kuso_penguin.dance.no_title");
            return;
        }
        if (clean.length() > DanceLibrary.MAX_TITLE_CHARS) {
            clean = clean.substring(0, DanceLibrary.MAX_TITLE_CHARS);
        }
        if (upload != null) {
            message = Component.translatable("gui.kuso_penguin.dance.busy");
            return;
        }
        byte[] bytes = prepared.canonical().bytes();
        upload = new Upload(bytes, clean, prepared.canonical().hash());
        send(DancePackets.Action.BEGIN_UPLOAD, upload.ticket, "", upload.title, bytes.length,
            upload.expectedHash);
        message = Component.translatable("gui.kuso_penguin.dance.waiting");
    }

    public static void cancelUpload() {
        importGeneration++;
        if (importJob != null) {
            importJob.cancel(true);
            importJob = null;
        }
        if (upload != null) {
            send(DancePackets.Action.CANCEL_UPLOAD, upload.ticket, "", "", 0, "");
            upload = null;
        }
    }

    /** Actor prefetch waits for capacity instead of failing the fifth distinct nearby clip. */
    public static boolean hasDownloadCapacity() {
        return PENDING.size() < MAX_PENDING_DOWNLOADS;
    }

    public static CompletableFuture<PenguinAnimationClip> clip(String hash) {
        if (!DanceLibrary.isStrictHash(hash)) {
            return CompletableFuture.failedFuture(new IOException("Invalid dance hash"));
        }
        Cached cached = CACHE.get(hash);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.clip());
        }
        Pending pending = PENDING.get(hash);
        if (pending != null) {
            return pending.result;
        }
        if (PENDING.size() >= MAX_PENDING_DOWNLOADS) {
            return CompletableFuture.failedFuture(new IOException("Dance download queue full"));
        }
        Pending created = new Pending(hash);
        PENDING.put(hash, created);
        return created.result;
    }

    public static PenguinAnimationClip cachedClip(String hash) {
        Cached cached = CACHE.get(hash);
        return cached == null ? null : cached.clip();
    }

    public static void onReply(DancePackets.Reply reply) {
        if (upload != null && upload.ticket.equals(reply.ticket())) {
            uploadReply(reply);
            return;
        }
        for (Pending pending : PENDING.values()) {
            if (pending.ticket.equals(reply.ticket())
                && reply.action() == DancePackets.Action.DOWNLOAD) {
                downloadReply(pending, reply);
                return;
            }
        }
        if (reply.action() == DancePackets.Action.DELETE
            || reply.action() == DancePackets.Action.RENAME
            || reply.action() == DancePackets.Action.SET_PREF
            || reply.action() == DancePackets.Action.SET_SLOT) {
            message = reply.success()
                ? Component.translatable("gui.kuso_penguin.dance.accepted")
                : Component.translatable(reply.message());
        }
    }

    private static void uploadReply(DancePackets.Reply reply) {
        if (reply.action() == DancePackets.Action.BEGIN_UPLOAD) {
            if (!reply.success()) {
                message = Component.translatable(reply.message());
                upload = null;
                return;
            }
            if ("progress".equals(reply.message())) {
                if (reply.totalBytes() < upload.ack || reply.totalBytes() > upload.sent) {
                    failUpload(new IOException("Invalid upload acknowledgement"));
                    return;
                }
                upload.ack = reply.totalBytes();
            } else if (reply.totalBytes() != upload.bytes.length || upload.sent != 0) {
                failUpload(new IOException("Invalid upload begin bounds"));
                return;
            }
            upload.limit = Math.min(upload.bytes.length,
                upload.ack + 4 * DancePackets.MAX_CHUNK);
            return;
        }
        if (reply.action() == DancePackets.Action.END_UPLOAD) {
            if (!reply.success()) {
                message = Component.translatable(reply.message());
                upload = null;
                return;
            }
            upload = null;
            message = Component.translatable("gui.kuso_penguin.dance.saved");
        }
    }

    private static void failUpload(Throwable throwable) {
        if (upload != null) {
            send(DancePackets.Action.CANCEL_UPLOAD, upload.ticket, "", "", 0, "");
        }
        upload = null;
        error(throwable);
    }

    private static void downloadReply(Pending pending, DancePackets.Reply reply) {
        if (!reply.success()) {
            if (reply.message().endsWith(".busy") && pending.attempts < 3) {
                pending.requested = false;
                pending.waiting = true;
                pending.retryAt = now() + 40L;
                if (activeDownload == pending) {
                    activeDownload = null;
                }
                return;
            }
            fail(pending, new IOException(reply.message()));
            return;
        }
        if (!reply.clipId().equals(pending.id)
            || reply.totalBytes() <= 0 || reply.totalBytes() > DanceLibrary.MAX_FILE_BYTES) {
            fail(pending, new IOException("Invalid dance download bounds"));
            return;
        }
        pending.bytes = new byte[reply.totalBytes()];
        pending.received = 0;
        pending.waiting = false;
        activeDownload = pending;
    }

    public static void onChunk(DancePackets.Chunk chunk) {
        Pending pending = activeDownload;
        if (pending == null || pending.bytes == null
            || !pending.ticket.equals(chunk.ticket())) {
            return;
        }
        byte[] data = chunk.data();
        if (chunk.offset() != pending.received || data.length <= 0
            || data.length > pending.bytes.length - pending.received) {
            fail(pending, new IOException("Invalid dance download offset"));
            return;
        }
        System.arraycopy(data, 0, pending.bytes, pending.received, data.length);
        pending.received += data.length;
        if (pending.received != pending.bytes.length) {
            return;
        }
        byte[] bytes = pending.bytes;
        pending.bytes = null;
        activeDownload = null;
        PENDING.remove(pending.id, pending);
        long generation = connectionGeneration;
        try {
            IO.execute(() -> {
                try {
                    DanceLibrary.Canonical canonical = DanceLibrary.canonicalize(bytes);
                    if (!canonical.hash().equals(pending.id)) {
                        throw new IOException("Downloaded dance hash mismatch");
                    }
                    MC.execute(() -> {
                        if (generation != connectionGeneration) {
                            pending.result.completeExceptionally(
                                new IOException("Disconnected"));
                            return;
                        }
                        putCache(canonical);
                        pending.result.complete(canonical.clip());
                    });
                } catch (Exception exception) {
                    MC.execute(() -> {
                        if (generation == connectionGeneration) {
                            fail(pending, exception);
                        } else {
                            pending.result.completeExceptionally(new IOException("Disconnected"));
                        }
                    });
                }
            });
        } catch (RejectedExecutionException exception) {
            fail(pending, exception);
        }
    }

    private static void putCache(DanceLibrary.Canonical canonical) {
        Cached old = CACHE.remove(canonical.hash());
        if (old != null) {
            cacheBytes -= old.bytes();
        }
        int size = canonical.bytes().length;
        CACHE.put(canonical.hash(), new Cached(canonical, canonical.clip(), size));
        cacheBytes += size;
        while (CACHE.size() > MAX_CACHE_ENTRIES || cacheBytes > MAX_CACHE_BYTES) {
            var iterator = CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            Cached removed = iterator.next().getValue();
            cacheBytes -= removed.bytes();
            iterator.remove();
        }
    }

    private static void fail(Pending pending, Throwable throwable) {
        pending.bytes = null;
        pending.waiting = false;
        pending.requested = false;
        PENDING.remove(pending.id, pending);
        if (activeDownload == pending) {
            activeDownload = null;
        }
        pending.result.completeExceptionally(throwable);
    }

    public static void queueControl(DancePackets.Control control) {
        if (control == null) {
            return;
        }
        if (CONTROLS.size() >= MAX_CONTROL_QUEUE) {
            message = Component.translatable("gui.kuso_penguin.dance.busy");
            return;
        }
        CONTROLS.addLast(control);
        message = Component.translatable("gui.kuso_penguin.dance.queued");
    }

    public static void tick() {
        if (MC.level == null || MC.getConnection() == null) {
            return;
        }
        long tick = now();
        if (upload != null && !upload.ended) {
            for (int chunks = 0; chunks < 4 && upload.sent < upload.limit; chunks++) {
                int length = Math.min(DancePackets.MAX_CHUNK, upload.limit - upload.sent);
                PacketDistributor.sendToServer(new DancePackets.Chunk(upload.ticket, upload.sent,
                    Arrays.copyOfRange(upload.bytes, upload.sent, upload.sent + length)));
                upload.sent += length;
            }
            if (upload.sent == upload.bytes.length) {
                upload.ended = true;
                send(DancePackets.Action.END_UPLOAD, upload.ticket, "", "", upload.bytes.length,
                    upload.expectedHash);
                message = Component.translatable("gui.kuso_penguin.dance.saving");
            }
        }
        if (!CONTROLS.isEmpty() && tick >= nextControlTick) {
            send(CONTROLS.removeFirst());
            nextControlTick = tick + 2L;
        }
        if (activeDownload != null && tick - activeDownload.started > 1200L) {
            fail(activeDownload, new IOException("Dance download timed out"));
        }
        if (activeDownload == null) {
            for (Pending pending : PENDING.values()) {
                if (!pending.requested && pending.bytes == null
                    && (!pending.waiting || tick >= pending.retryAt)) {
                    activeDownload = pending;
                    pending.requested = true;
                    pending.waiting = false;
                    pending.started = tick;
                    pending.attempts++;
                    send(DancePackets.Action.DOWNLOAD, pending.ticket, pending.id, "", 0, "");
                    break;
                }
            }
        }
    }

    public static void clear() {
        cancelUpload();
        connectionGeneration++;
        importGeneration++;
        if (importJob != null) {
            importJob.cancel(true);
        }
        importJob = null;
        upload = null;
        prepared = null;
        preparedGeneration++;
        for (Pending pending : PENDING.values()) {
            pending.result.completeExceptionally(new IOException("Disconnected"));
        }
        PENDING.clear();
        activeDownload = null;
        CACHE.clear();
        cacheBytes = 0;
        CONTROLS.clear();
        nextControlTick = 0;
        message = Component.empty();
    }
}
