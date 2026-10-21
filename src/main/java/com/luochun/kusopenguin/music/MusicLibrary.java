package com.luochun.kusopenguin.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, world-scoped server music library.
 *
 * <p>Audio files live under {@code kusopenguin/music/audio/<sha256>.wav}; the index lives next
 * to them in {@code index.json}. Imports validate the WAV on the server, deduplicate by the
 * SHA-256 of the complete bytes, and publish both files with atomic replacement. Library
 * methods are synchronized; upload/read calls may run on a worker thread.</p>
 */
public final class MusicLibrary {
    public static final long MAX_STORED_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_TRACKS_PER_OWNER = 20;
    public static final int MAX_TRACKS = 100;
    public static final int MAX_TITLE_CHARS = 128;

    private static final int SCHEMA = 1;
    private static final int CANONICAL_HEADER_BYTES = 44;
    private static final String INDEX_FILE = "index.json";
    private static final String AUDIO_DIR = "audio";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/MusicLibrary");

    /**
     * Keyed weakly because a Minecraft server must never be retained by this cache. The library
     * itself only stores the world path.
     */
    private static final Map<MinecraftServer, MusicLibrary> INSTANCES = new WeakHashMap<>();

    private final Path root;
    private final Path audioDir;
    private final Path indexFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> byHash = new LinkedHashMap<>();

    /**
     * Stored track metadata. {@code totalBytes} is the complete WAV file size, including the
     * 44-byte canonical header.
     */
    public record Track(String id, String title, UUID owner, int sampleRate, int dataOffset,
                        int pcmBytes, int totalBytes, int durationTicks) {
    }

    private record Entry(Track track, String hash) {
    }

    private MusicLibrary(Path root) {
        // Native world roots can contain ./ or ../ even when their destination is absolute.
        // Containment checks must compare paths in the same normalized coordinate system.
        this.root = root.toAbsolutePath().normalize();
        this.audioDir = this.root.resolve(AUDIO_DIR);
        this.indexFile = this.root.resolve(INDEX_FILE);
        loadIndex();
    }

    /** Get (or lazily create and load) the library for this server. */
    public static synchronized MusicLibrary get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        MusicLibrary library = INSTANCES.get(server);
        if (library == null) {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            library = new MusicLibrary(worldRoot.resolve("kusopenguin/music"));
            INSTANCES.put(server, library);
        }
        return library;
    }

    /** Drop the live library cache entry. Persisted files are untouched. */
    public static synchronized void close(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }

    /** Track metadata, or {@code null} when the id is not strict lowercase SHA-256 hex/present. */
    public synchronized Track get(String id) {
        Entry entry = find(id);
        return entry == null ? null : entry.track();
    }

    /** Immutable catalog copy sorted by title, then id. */
    public synchronized List<Track> catalog() {
        List<Track> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            result.add(entry.track());
        }
        result.sort(Comparator.comparing(Track::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Track::id));
        return Collections.unmodifiableList(result);
    }

    /**
     * Validate and persist a complete WAV upload.
     *
     * <p>Quota accounting uses the sizes recorded in the persisted index. A duplicate hash is
     * accepted as a no-op and returns the existing track, keeping existing metadata.</p>
     */
    public synchronized Track importTrack(UUID owner, String title, byte[] wav) throws IOException {
        Objects.requireNonNull(owner, "owner");
        String cleanTitle;
        try {
            cleanTitle = cleanTitle(title);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        WaveFile.Header header = WaveFile.inspect(wav);
        if (header.dataOffset() != CANONICAL_HEADER_BYTES
            || wav.length != CANONICAL_HEADER_BYTES + header.pcmBytes()) {
            throw new IOException("Track must be a canonical WAV with a 44-byte header and no extra bytes");
        }
        String hash = WaveFile.hash(wav);

        Entry duplicate = byHash.get(hash);
        if (duplicate != null) {
            return duplicate.track();
        }

        if (entries.size() >= MAX_TRACKS) {
            throw new IOException("Music library is full: " + MAX_TRACKS + " tracks maximum");
        }
        int owned = 0;
        long storedBytes = 0L;
        for (Entry entry : entries.values()) {
            storedBytes += entry.track().totalBytes();
            if (owner.equals(entry.track().owner())) {
                owned++;
            }
        }
        if (owned >= MAX_TRACKS_PER_OWNER) {
            throw new IOException("Owner already has " + MAX_TRACKS_PER_OWNER + " tracks");
        }
        if (wav.length > MAX_STORED_BYTES - storedBytes) {
            throw new IOException("Music library storage limit of 512 MiB would be exceeded");
        }

        String id = hash;
        if (entries.containsKey(id)) {
            // A hash collision with different metadata would make id/hash accounting ambiguous.
            throw new IOException("Track id already exists with a different hash");
        }

        Track track = new Track(id, cleanTitle, owner, header.sampleRate(), header.dataOffset(),
            header.pcmBytes(), wav.length, header.durationTicks());
        Entry entry = new Entry(track, hash);

        Files.createDirectories(audioDir);
        Path audioTarget = audioPath(id);
        Path tempAudio = Files.createTempFile(audioDir, "upload-", ".tmp");
        Path tempIndex = null;
        try {
            Files.write(tempAudio, wav);
            tempIndex = Files.createTempFile(root, "index-", ".tmp");

            List<Entry> prospective = new ArrayList<>(entries.size() + 1);
            prospective.addAll(entries.values());
            prospective.add(entry);
            Files.writeString(tempIndex, toJson(prospective), StandardCharsets.UTF_8);

            // Audio first: until the index publishes it, a crash leaves only a harmless file.
            atomicReplace(tempAudio, audioTarget);
            try {
                atomicReplace(tempIndex, indexFile);
                tempIndex = null;
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(audioTarget);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }

            entries.put(id, entry);
            byHash.put(hash, entry);
            return track;
        } finally {
            deleteTemp(tempAudio);
            if (tempIndex != null) {
                deleteTemp(tempIndex);
            }
        }
    }

    /** Read complete stored WAV bytes. Intended for worker threads. */
    public synchronized byte[] read(String id) throws IOException {
        Entry entry = find(id);
        if (entry == null) {
            throw new IOException("Unknown track id: " + id);
        }
        Path audio = audioPath(id);
        if (!Files.isRegularFile(audio)) {
            throw new IOException("Track audio file is missing: " + id);
        }
        Track track = entry.track();
        long storedSize = Files.size(audio);
        if (storedSize != track.totalBytes()) {
            throw new IOException("Track audio size changed on disk: expected " + track.totalBytes()
                + " bytes, found " + storedSize);
        }
        byte[] data = Files.readAllBytes(audio);
        if (data.length != track.totalBytes()) {
            throw new IOException("Track audio size changed while reading: expected " + track.totalBytes()
                + " bytes, found " + data.length);
        }
        if (!WaveFile.hash(data).equals(entry.hash())) {
            throw new IOException("Track audio content hash does not match the index: " + id);
        }
        WaveFile.Header header = WaveFile.inspect(data);
        if (header.sampleRate() != track.sampleRate() || header.pcmBytes() != track.pcmBytes()
            || header.dataOffset() != track.dataOffset()) {
            throw new IOException("Track audio metadata changed on disk: " + id);
        }
        return data;
    }

    /** Stored audio path, or {@code null} when the id is not strict lowercase hex/present. */
    public synchronized Path path(String id) {
        return find(id) == null ? null : audioPath(id);
    }

    private Entry find(String id) {
        if (!isStrictId(id)) {
            return null;
        }
        return entries.get(id);
    }

    private Path audioPath(String id) {
        return audioDir.resolve(id + ".wav");
    }

    private void loadIndex() {
        if (!Files.exists(indexFile)) {
            return;
        }
        if (!Files.isRegularFile(indexFile)) {
            throw corrupt("music index is not a regular file: " + indexFile, null);
        }
        JsonObject json;
        try {
            String text = Files.readString(indexFile, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("root is not a JSON object");
            }
            json = parsed.getAsJsonObject();
        } catch (IOException e) {
            throw corrupt("cannot read " + indexFile, e);
        } catch (RuntimeException e) {
            throw corrupt("invalid JSON in " + indexFile, e);
        }

        int schema = requireInt(json, "schema");
        if (schema != SCHEMA) {
            throw corrupt("unsupported music index schema " + schema + " (expected " + SCHEMA + ")", null);
        }
        JsonElement tracksElement = json.get("tracks");
        if (tracksElement == null || !tracksElement.isJsonArray()) {
            throw corrupt("music index has no tracks array", null);
        }
        JsonArray tracks = tracksElement.getAsJsonArray();
        if (tracks.size() > MAX_TRACKS) {
            throw corrupt("music index has " + tracks.size() + " tracks; maximum is " + MAX_TRACKS, null);
        }

        for (int i = 0; i < tracks.size(); i++) {
            JsonElement element = tracks.get(i);
            if (!element.isJsonObject()) {
                throw corrupt("music index track " + i + " is not an object", null);
            }
            Entry entry = parseEntry(element.getAsJsonObject(), i);
            if (entries.putIfAbsent(entry.track().id(), entry) != null) {
                throw corrupt("duplicate track id " + entry.track().id(), null);
            }
            if (byHash.putIfAbsent(entry.hash(), entry) != null) {
                throw corrupt("duplicate track hash " + entry.hash(), null);
            }
        }
    }

    private Entry parseEntry(JsonObject json, int index) {
        String where = "music index track " + index;
        String id;
        String file;
        String title;
        UUID owner;
        String hash;
        int sampleRate;
        int dataOffset;
        int pcmBytes;
        int totalBytes;
        int durationTicks;
        try {
            id = requireString(json, "id");
            file = requireString(json, "file");
            title = cleanTitle(requireString(json, "title"));
            owner = UUID.fromString(requireString(json, "owner"));
            hash = requireString(json, "hash");
            sampleRate = requireInt(json, "sampleRate");
            dataOffset = requireInt(json, "dataOffset");
            pcmBytes = requireInt(json, "pcmBytes");
            totalBytes = requireInt(json, "totalBytes");
            durationTicks = requireInt(json, "durationTicks");
        } catch (RuntimeException e) {
            throw corrupt(where + " has invalid metadata", e);
        }

        if (!isStrictId(id)) {
            throw corrupt(where + " id is not strict 64-character lowercase hex", null);
        }
        if (!isStrictId(hash)) {
            throw corrupt(where + " hash is not strict 64-character lowercase hex", null);
        }
        if (!file.equals(id + ".wav")) {
            throw corrupt(where + " file name does not match its track id: " + file, null);
        }
        if (file.indexOf('/') >= 0 || file.indexOf('\\') >= 0 || file.indexOf(':') >= 0
            || Path.of(file).isAbsolute()) {
            throw corrupt(where + " contains an invalid path instead of a plain file name: " + file, null);
        }
        if (sampleRate < 8000 || sampleRate > 48000) {
            throw corrupt(where + " has unsupported sample rate " + sampleRate, null);
        }
        if (dataOffset != CANONICAL_HEADER_BYTES) {
            throw corrupt(where + " has unexpected data offset " + dataOffset, null);
        }
        if (pcmBytes <= 0 || (pcmBytes & 1) != 0 || pcmBytes > WaveFile.MAX_BYTES) {
            throw corrupt(where + " has invalid PCM byte count " + pcmBytes, null);
        }
        if (totalBytes != (long) pcmBytes + CANONICAL_HEADER_BYTES || totalBytes > WaveFile.MAX_BYTES) {
            throw corrupt(where + " has invalid total byte count " + totalBytes, null);
        }
        long frames = pcmBytes / 2L;
        long expectedTicks = (frames * 20L + sampleRate - 1L) / sampleRate;
        if (durationTicks != expectedTicks || durationTicks > WaveFile.MAX_SECONDS * 20L) {
            throw corrupt(where + " has invalid duration " + durationTicks + " ticks", null);
        }

        Path audio = audioDir.resolve(file).normalize();
        if (!audio.startsWith(audioDir)) {
            throw corrupt(where + " file escapes the music directory: " + file, null);
        }
        if (!Files.isRegularFile(audio)) {
            throw corrupt(where + " audio file is missing: " + audio, null);
        }
        try {
            if (Files.size(audio) != totalBytes) {
                throw corrupt(where + " audio file size does not match the index: " + audio, null);
            }
        } catch (IOException e) {
            throw corrupt(where + " audio file cannot be read: " + audio, e);
        }

        Track track = new Track(id, title, owner, sampleRate, dataOffset, pcmBytes, totalBytes, durationTicks);
        return new Entry(track, hash);
    }

    private String toJson(List<Entry> list) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        JsonArray tracks = new JsonArray();
        for (Entry entry : list) {
            Track track = entry.track();
            JsonObject item = new JsonObject();
            item.addProperty("id", track.id());
            item.addProperty("file", track.id() + ".wav");
            item.addProperty("title", track.title());
            item.addProperty("owner", track.owner().toString());
            item.addProperty("hash", entry.hash());
            item.addProperty("sampleRate", track.sampleRate());
            item.addProperty("dataOffset", track.dataOffset());
            item.addProperty("pcmBytes", track.pcmBytes());
            item.addProperty("totalBytes", track.totalBytes());
            item.addProperty("durationTicks", track.durationTicks());
            tracks.add(item);
        }
        json.add("tracks", tracks);
        return GSON.toJson(json);
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Only this failure permits the documented non-atomic replace fallback.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemp(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            LOGGER.warn("Could not delete temporary music file {}", temp, e);
        }
    }

    private static String cleanTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("Track title is required");
        }
        String cleaned = title.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Track title must not be empty");
        }
        if (cleaned.length() > MAX_TITLE_CHARS) {
            throw new IllegalArgumentException("Track title exceeds " + MAX_TITLE_CHARS + " characters");
        }
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            int type = Character.getType(c);
            if (c < 0x20 || c == 0x7f || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Track title must be a single clean line");
            }
        }
        return cleaned;
    }

    private static boolean isStrictId(String id) {
        if (id == null || id.length() != 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static int requireInt(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric field '" + name + "'");
        }
        long value = element.getAsLong();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("field '" + name + "' is outside the int range");
        }
        return (int) value;
    }

    private static String requireString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing string field '" + name + "'");
        }
        return element.getAsString();
    }

    private static IllegalStateException corrupt(String message, Throwable cause) {
        String full = "Corrupt music library: " + message;
        LOGGER.error(full);
        return new IllegalStateException(full, cause);
    }
}
