package com.luochun.kusopenguin.client.music;

import com.luochun.kusopenguin.music.DiscPackets;
import com.luochun.kusopenguin.music.WaveFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-thread transfer state; bounded workers alone own decoding and filesystem IO. */
public final class DiscTransfers {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ExecutorService IO = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "KusoPenguin-MusicIO"); t.setDaemon(true); return t; });
    private static long connectionGeneration, importGeneration;
    private static Future<?> decodeJob;
    private static Upload upload;
    private static Component message = Component.empty();
    private static final Map<String, Pending> AUDIO = new LinkedHashMap<>();
    private static Pending download;
    private static final UUID ZERO = new UUID(0, 0);
    private static final class Upload {
        final UUID ticket = UUID.randomUUID(); final byte[] bytes; final String title;
        int sent, ack, limit; boolean ended;
        Upload(byte[] bytes, String title) { this.bytes = bytes; this.title = title; }
    }
    private static final class Pending {
        final String id; final UUID ticket = UUID.randomUUID();
        final CompletableFuture<Path> result = new CompletableFuture<>();
        byte[] bytes; int received, attempts; long retryAt, started;
        boolean waiting, requested;
        Pending(String id) { this.id = id; }
    }
    private DiscTransfers() {}
    private static long now() { return MC.level == null ? 0 : MC.level.getGameTime(); }
    private static void send(DiscPackets.Action action, UUID ticket, String id, String title, int size) {
        if (MC.getConnection() != null) PacketDistributor.sendToServer(new DiscPackets.Request(action, ticket, id, title, size));
    }
    private static void error(Throwable error) {
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        message = Component.translatable("gui.kuso_penguin.disc.error", Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
    }
    public static void reportError(Throwable error) { error(error); }
    public static Component status() {
        if (upload != null && !upload.ended) return Component.translatable("gui.kuso_penguin.disc.uploading",
                upload.ack * 100L / upload.bytes.length);
        return message;
    }
    public static boolean uploading() { return upload != null || decodeJob != null; }

    public static void importFile(Path file) {
        cancelUpload();
        long generation = importGeneration, connection = connectionGeneration;
        Object network = MC.getConnection();
        message = Component.translatable("gui.kuso_penguin.disc.decoding");
        try {
            decodeJob = IO.submit(() -> {
                try {
                    byte[] wav = MusicImport.normalize(file);
                    String title = file.getFileName().toString().replaceAll("\\.[^.]+$", "")
                            .replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").strip();
                    if (title.isEmpty()) title = "Music";
                    if (title.length() > 128) title = title.substring(0, 128);
                    String finalTitle = title;
                    MC.execute(() -> {
                        if (generation != importGeneration || connection != connectionGeneration || network != MC.getConnection()) return;
                        decodeJob = null;
                        upload = new Upload(wav, finalTitle);
                        send(DiscPackets.Action.BEGIN_UPLOAD, upload.ticket, "", upload.title, wav.length);
                        message = Component.translatable("gui.kuso_penguin.disc.waiting");
                    });
                } catch (Exception e) {
                    MC.execute(() -> { if (generation == importGeneration && connection == connectionGeneration) { decodeJob = null; error(e); } });
                }
            });
        } catch (RejectedExecutionException e) { decodeJob = null; error(e); }
    }

    public static void cancelUpload() {
        importGeneration++;
        if (decodeJob != null) decodeJob.cancel(true);
        decodeJob = null;
        if (upload != null && !upload.ended) send(DiscPackets.Action.CANCEL_UPLOAD, upload.ticket, "", "", 0);
        upload = null;
    }

    public static void onReply(DiscPackets.Reply reply) {
        if (upload != null && upload.ticket.equals(reply.ticket())) {
            if (!reply.success()) { message = Component.translatable(reply.message()); upload = null; return; }
            if (reply.action() == DiscPackets.Action.BEGIN_UPLOAD) {
                if (reply.message().equals("progress")) {
                    if (reply.totalBytes() < upload.ack || reply.totalBytes() > upload.sent) { cancelUpload(); error(new IOException("Invalid upload acknowledgement")); return; }
                    upload.ack = reply.totalBytes();
                } else if (reply.totalBytes() != upload.bytes.length || upload.sent != 0) return;
                // Credit is a byte window, including old in-flight chunks. A partial ACK must
                // not grant four additional chunks and overflow the next server tick's budget.
                upload.limit = Math.min(upload.bytes.length, upload.ack + 4 * WaveFile.MAX_CHUNK);
            } else if (reply.action() == DiscPackets.Action.END_UPLOAD) {
                DiscClient.selected(reply.trackId()); upload = null;
                message = Component.translatable("gui.kuso_penguin.disc.saved");
            }
            return;
        }
        if (download != null && download.ticket.equals(reply.ticket()) && reply.action() == DiscPackets.Action.DOWNLOAD) {
            if (!reply.success()) {
                Pending failed = download; download = null; failed.requested = false;
                if (reply.message().endsWith(".busy") && failed.attempts < 5) { failed.retryAt = now() + 40; failed.waiting = true; }
                else fail(failed, new IOException(reply.message()));
            } else if (!reply.trackId().equals(download.id) || reply.totalBytes() < 44 || reply.totalBytes() > WaveFile.MAX_BYTES) {
                Pending failed = download; download = null; fail(failed, new IOException("Invalid download bounds"));
            } else download.bytes = new byte[reply.totalBytes()];
            return;
        }
        if (reply.action() == DiscPackets.Action.SELECT)
            message = reply.success() ? Component.translatable("gui.kuso_penguin.disc.written") : Component.translatable(reply.message());
    }

    public static void tick() {
        if (MC.level == null || MC.getConnection() == null) return;
        if (upload != null && !upload.ended) {
            for (int chunks = 0; chunks < 4 && upload.sent < upload.limit; chunks++) {
                int count = Math.min(WaveFile.MAX_CHUNK, upload.limit - upload.sent);
                PacketDistributor.sendToServer(new DiscPackets.Chunk(upload.ticket, upload.sent,
                        Arrays.copyOfRange(upload.bytes, upload.sent, upload.sent + count)));
                upload.sent += count;
            }
            if (upload.sent == upload.bytes.length) {
                upload.ended = true;
                send(DiscPackets.Action.END_UPLOAD, upload.ticket, "", "", upload.bytes.length);
                message = Component.translatable("gui.kuso_penguin.disc.saving");
            }
        }
        if (download != null && now() - download.started > 1200) {
            Pending failed = download; download = null; fail(failed, new IOException("Download timed out"));
        }
        if (download == null) for (Pending pending : AUDIO.values()) {
            if (!pending.result.isDone() && pending.waiting && now() >= pending.retryAt) {
                download = pending; pending.waiting = false; pending.requested = true;
                pending.started = now(); pending.attempts++;
                send(DiscPackets.Action.DOWNLOAD, pending.ticket, pending.id, "", 0); break;
            }
        }
    }

    public static CompletableFuture<Path> audio(String id) {
        if (!id.matches("[0-9a-f]{64}")) return CompletableFuture.failedFuture(new IOException("Invalid music id"));
        Pending old = AUDIO.get(id); if (old != null) return old.result;
        long unfinished = AUDIO.values().stream().filter(p -> !p.result.isDone()).count();
        if (unfinished >= 16) return CompletableFuture.failedFuture(new IOException("Music queue full"));
        Pending pending = new Pending(id); AUDIO.put(id, pending);
        long generation = connectionGeneration;
        try {
            IO.execute(() -> {
                Path path = cache(id); boolean valid = false;
                try {
                    if (Files.isRegularFile(path) && Files.size(path) <= WaveFile.MAX_BYTES) {
                        byte[] bytes = Files.readAllBytes(path); WaveFile.inspect(bytes);
                        valid = WaveFile.hash(bytes).equals(id);
                    }
                } catch (IOException ignored) { /* A missing/corrupt cache is fetched again. */ }
                boolean cached = valid;
                MC.execute(() -> { if (generation == connectionGeneration) { if (cached) pending.result.complete(path); else pending.waiting = true; } });
            });
        } catch (RejectedExecutionException e) { fail(pending, e); }
        return pending.result;
    }

    public static void onChunk(DiscPackets.Chunk chunk) {
        Pending pending = download;
        if (pending == null || !pending.ticket.equals(chunk.ticket()) || pending.bytes == null) return;
        byte[] data = chunk.data();
        if (chunk.offset() != pending.received || data.length == 0 || data.length > pending.bytes.length - pending.received) {
            download = null; fail(pending, new IOException("Invalid download offset")); return;
        }
        System.arraycopy(data, 0, pending.bytes, pending.received, data.length); pending.received += data.length;
        if (pending.received != pending.bytes.length) return;
        download = null; pending.requested = false;
        byte[] bytes = pending.bytes; pending.bytes = null; long generation = connectionGeneration;
        try {
            IO.execute(() -> {
                try {
                    WaveFile.inspect(bytes);
                    if (!WaveFile.hash(bytes).equals(pending.id)) throw new IOException("Downloaded music hash mismatch");
                    Path target = cache(pending.id); Files.createDirectories(target.getParent());
                    Path temp = Files.createTempFile(target.getParent(), "download-", ".tmp");
                    try {
                        Files.write(temp, bytes);
                        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(temp); }
                    MC.execute(() -> { if (generation == connectionGeneration) pending.result.complete(target); });
                } catch (Exception e) { MC.execute(() -> { if (generation == connectionGeneration) fail(pending, e); }); }
            });
        } catch (RejectedExecutionException e) { fail(pending, e); }
    }
    private static Path cache(String id) { return MC.gameDirectory.toPath().resolve("kusopenguin/music-cache").resolve(id + ".wav"); }
    private static void fail(Pending pending, Throwable error) {
        pending.bytes = null; pending.waiting = false; AUDIO.remove(pending.id, pending);
        pending.result.completeExceptionally(error);
    }
    public static void clear() {
        cancelUpload(); connectionGeneration++; download = null;
        for (Pending pending : AUDIO.values()) if (!pending.result.isDone()) pending.result.completeExceptionally(new IOException("Disconnected"));
        AUDIO.clear(); message = Component.empty();
    }
}
