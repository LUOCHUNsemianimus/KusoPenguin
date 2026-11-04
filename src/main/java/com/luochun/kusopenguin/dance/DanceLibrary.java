package com.luochun.kusopenguin.dance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luochun.kusopenguin.client.PenguinAnimationClip;
import com.luochun.kusopenguin.client.PenguinRig;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World-scoped, content-addressed library for player-uploaded penguin dance clips plus the
 * per-player trigger/slot preferences.
 *
 * <p>Only canonical schema-1 JSON is accepted. The complete file is bounded to 2 MiB and
 * 901 frames, a bounded strict JSON scan rejects excessive nesting and duplicate keys before
 * Gson parses it, every root translation is validated and normalized to zero <em>before</em> the
 * immutable {@link PenguinAnimationClip} parser sees it, the parsed bind must equal the canonical
 * taunt bind, and the stored file name/hash is the SHA-256 of those normalized bytes. Files are
 * published with a temporary file plus atomic move; network-supplied path names are never used.
 * Methods may be called from a bounded worker thread.</p>
 *
 * <p>Clip content is stored once per hash. Each player owns an independent registration (title,
 * quota accounting and preferences) that points at those shared bytes. Deleting one registration
 * keeps the file while any other registration remains; deleting the last registration removes the
 * file. An operator deleting a hash they do not own removes every registration for that hash.</p>
 */
public final class DanceLibrary {
    public static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    public static final int MAX_FRAMES = 901;
    public static final float MAX_SECONDS = 30.0F;
    public static final int MAX_CLIPS_PER_PLAYER = 5;
    public static final long MAX_BYTES_PER_PLAYER = 10L * 1024L * 1024L;
    public static final long MAX_GLOBAL_BYTES = 128L * 1024L * 1024L;
    public static final int MAX_TITLE_CHARS = 128;
    public static final int MAX_CLIP_ID_CHARS = 64;
    public static final int STYLE_SECONDS = 5;
    public static final int MAX_JSON_DEPTH = 32;
    public static final long MAX_JSON_TOKENS = 500_000L;

    public static final String BUILTIN_HIP = "builtin:hip";
    public static final String BUILTIN_POLE = "builtin:pole";
    public static final String BUILTIN_WAVE = "builtin:wave";
    public static final String BUILTIN_CYCLE = "builtin:cycle";

    private static final int SCHEMA = 1;
    private static final String INDEX_FILE = "index.json";
    private static final String PREFS_FILE = "preferences.json";
    private static final String CLIP_DIR = "clips";
    private static final String TAUNT_RESOURCE = "/assets/kuso_penguin/animations/taunt.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson CLIP_GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("KusoPenguin/DanceLibrary");

    /** Immutable metadata for one builtin style. Durations are the existing five-second cadence. */
    public record Builtin(String id, String translationKey, float durationSeconds) {
        public Builtin {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(translationKey, "translationKey");
            if (!id.equals(BUILTIN_HIP) && !id.equals(BUILTIN_POLE) && !id.equals(BUILTIN_WAVE)
                && !id.equals(BUILTIN_CYCLE)) {
                throw new IllegalArgumentException("Not a builtin dance id: " + id);
            }
            if (!Float.isFinite(durationSeconds) || durationSeconds <= 0
                || durationSeconds > MAX_SECONDS) {
                throw new IllegalArgumentException("Invalid builtin duration");
            }
        }
    }

    /** Immutable stored clip metadata. {@code id} always equals {@code hash}. */
    public record Clip(String id, String hash, UUID owner, String title, int bytes, float duration,
                       int frames) {
        public Clip {
            Objects.requireNonNull(hash, "hash");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            if (!isStrictHash(hash)) {
                throw new IllegalArgumentException("Clip hash is not strict lowercase SHA-256");
            }
            if (!id.equals(hash)) {
                throw new IllegalArgumentException("Clip id must equal its content hash");
            }
            title = cleanTitle(title);
            if (bytes <= 0 || bytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Clip byte count out of range");
            }
            if (!Float.isFinite(duration) || duration <= 0 || duration > MAX_SECONDS) {
                throw new IllegalArgumentException("Clip duration out of range");
            }
            if (frames < 2 || frames > MAX_FRAMES) {
                throw new IllegalArgumentException("Clip frame count out of range");
            }
        }
    }

    /**
     * One validated canonical clip. The byte array is cloned on construction and on every
     * accessor call; the parsed clip is immutable and can be shared with clients without creating
     * a world-scoped library instance or re-running validation. {@link #canonicalize(byte[])} is
     * the only intended producer.
     */
    public static final class Canonical {
        private final byte[] bytes;
        private final String hash;
        private final float duration;
        private final int frames;
        private final PenguinAnimationClip clip;

        public Canonical(byte[] bytes, String hash, float duration, int frames,
                         PenguinAnimationClip clip) {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(clip, "clip");
            if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Canonical bytes out of range");
            }
            if (!isStrictHash(hash)) {
                throw new IllegalArgumentException("Canonical hash is not strict lowercase SHA-256");
            }
            if (!Float.isFinite(duration) || duration <= 0 || duration > MAX_SECONDS) {
                throw new IllegalArgumentException("Canonical duration out of range");
            }
            if (frames < 2 || frames > MAX_FRAMES) {
                throw new IllegalArgumentException("Canonical frame count out of range");
            }
            byte[] copy = bytes.clone();
            if (!sha256(copy).equals(hash)) {
                throw new IllegalArgumentException("Canonical hash does not match its bytes");
            }
            this.bytes = copy;
            this.hash = hash;
            this.duration = duration;
            this.frames = frames;
            this.clip = clip;
        }

        public byte[] bytes() {
            return bytes.clone();
        }

        public String hash() {
            return hash;
        }

        public float duration() {
            return duration;
        }

        public int frames() {
            return frames;
        }

        /** Parsed immutable clip; safe to parse once on the client. */
        public PenguinAnimationClip clip() {
            return clip;
        }
    }

    private static final Map<String, Builtin> BUILTINS_BY_ID;
    private static final List<Builtin> BUILTINS;

    static {
        Map<String, Builtin> map = new LinkedHashMap<>();
        map.put(BUILTIN_HIP, new Builtin(BUILTIN_HIP, "dance.kuso_penguin.builtin.hip", STYLE_SECONDS));
        map.put(BUILTIN_POLE, new Builtin(BUILTIN_POLE, "dance.kuso_penguin.builtin.pole", STYLE_SECONDS));
        map.put(BUILTIN_WAVE, new Builtin(BUILTIN_WAVE, "dance.kuso_penguin.builtin.wave", STYLE_SECONDS));
        map.put(BUILTIN_CYCLE, new Builtin(BUILTIN_CYCLE, "dance.kuso_penguin.builtin.cycle",
            STYLE_SECONDS * 3.0F));
        BUILTINS_BY_ID = Collections.unmodifiableMap(map);
        BUILTINS = List.copyOf(map.values());
    }

    /** Weak keys so a library instance can never retain a stopped Minecraft server. */
    private static final Map<MinecraftServer, DanceLibrary> INSTANCES = new WeakHashMap<>();

    /** Parsed once from the bundled canonical taunt animation and then shared read-only. */
    private static volatile PenguinAnimationClip referenceClipCache;

    private final Path root;
    private final Path clipDir;
    private final Path indexFile;
    private final Path prefsFile;
    /** Physical content keyed by hash; each hash is stored and counted once globally. */
    private final Map<String, Physical> physical = new LinkedHashMap<>();
    /** Per-hash registrations: owner -> that owner's title. Order is stable for primary lookup. */
    private final Map<String, Map<UUID, String>> ownersByHash = new LinkedHashMap<>();
    private final Map<UUID, Prefs> preferences = new HashMap<>();

    private record Physical(int bytes, float duration, int frames) {
        private Physical {
            if (bytes <= 0 || bytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Physical clip byte count out of range");
            }
            if (!Float.isFinite(duration) || duration <= 0 || duration > MAX_SECONDS) {
                throw new IllegalArgumentException("Physical clip duration out of range");
            }
            if (frames < 2 || frames > MAX_FRAMES) {
                throw new IllegalArgumentException("Physical clip frame count out of range");
            }
        }
    }

    private DanceLibrary(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.clipDir = this.root.resolve(CLIP_DIR);
        this.indexFile = this.root.resolve(INDEX_FILE);
        this.prefsFile = this.root.resolve(PREFS_FILE);
        try {
            Files.createDirectories(clipDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create dance library directory " + clipDir, e);
        }
        loadIndex();
        loadPreferences();
    }

    public static synchronized DanceLibrary get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        DanceLibrary library = INSTANCES.get(server);
        if (library == null) {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            library = new DanceLibrary(worldRoot.resolve("kusopenguin/dances"));
            INSTANCES.put(server, library);
        }
        return library;
    }

    /** Drop the live instance. Persisted files are untouched. */
    public static synchronized void close(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }

    public static boolean isBuiltin(String id) {
        return id != null && BUILTINS_BY_ID.containsKey(id);
    }

    public static List<Builtin> builtins() {
        return BUILTINS;
    }

    public static Builtin builtin(String id) {
        return id == null ? null : BUILTINS_BY_ID.get(id);
    }

    /**
     * Representative physical clip metadata by content hash, or {@code null} when no player has a
     * registration for that hash. Ownership-sensitive operations must use the owner-specific
     * methods below; this accessor never authorizes editing.
     */
    public synchronized Clip get(String hash) {
        Map<UUID, String> owners = findOwners(hash);
        if (owners == null || owners.isEmpty()) {
            return null;
        }
        Map.Entry<UUID, String> first = owners.entrySet().iterator().next();
        return makeClip(hash, first.getKey(), first.getValue());
    }

    /** One owner's registration for a hash, or {@code null} if that owner has not registered it. */
    public synchronized Clip ownedClip(UUID owner, String hash) {
        Objects.requireNonNull(owner, "owner");
        Map<UUID, String> owners = findOwners(hash);
        if (owners == null) {
            return null;
        }
        String title = owners.get(owner);
        return title == null ? null : makeClip(hash, owner, title);
    }

    /** All clips registered by one player, sorted by title then hash. */
    public synchronized List<Clip> ownedCatalog(UUID owner) {
        List<Clip> result = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : ownersByHash.entrySet()) {
            String title = entry.getValue().get(owner);
            if (title != null) {
                result.add(makeClip(entry.getKey(), owner, title));
            }
        }
        result.sort(Comparator.comparing(Clip::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Clip::hash));
        return List.copyOf(result);
    }

    public synchronized int ownedCount(UUID owner) {
        int count = 0;
        for (Map<UUID, String> owners : ownersByHash.values()) {
            if (owners.containsKey(owner)) {
                count++;
            }
        }
        return count;
    }

    public synchronized long ownedBytes(UUID owner) {
        long bytes = 0L;
        for (Map.Entry<String, Map<UUID, String>> entry : ownersByHash.entrySet()) {
            if (entry.getValue().containsKey(owner)) {
                bytes += physical.get(entry.getKey()).bytes();
            }
        }
        return bytes;
    }

    /** Total physical bytes; duplicate registrations of one hash count once. */
    public synchronized long storedBytes() {
        long bytes = 0L;
        for (Physical clip : physical.values()) {
            bytes += clip.bytes();
        }
        return bytes;
    }

    /**
     * Validate, normalize, hash and persist one canonical clip. Duplicate content for the same
     * owner is a no-op. When another owner already registered the same hash, this adds an
     * independent registration without writing or counting the bytes again. The caller has
     * already checked the transport-level reservation; this method performs the authoritative
     * per-player/global storage quota check before publishing files.
     */
    public synchronized Clip importClip(UUID owner, String title, byte[] raw) throws IOException {
        return importClip(owner, title, raw, "");
    }

    public synchronized Clip importClip(UUID owner, String title, byte[] raw, String expectedHash)
        throws IOException {
        Objects.requireNonNull(owner, "owner");
        String cleanTitle;
        try {
            cleanTitle = cleanTitle(title);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        Canonical canonical = canonicalize(raw);
        if (expectedHash != null && !expectedHash.isEmpty()
            && !expectedHash.equals(canonical.hash())) {
            throw new IOException("Dance content hash does not match the canonical bytes");
        }

        Clip existing = ownedClip(owner, canonical.hash());
        if (existing != null) {
            return existing;
        }

        Physical existingPhysical = physical.get(canonical.hash());
        if (existingPhysical != null && !matches(existingPhysical, canonical)) {
            throw new IOException("Dance clip metadata conflicts with the existing physical clip");
        }
        if (ownedCount(owner) >= MAX_CLIPS_PER_PLAYER) {
            throw new IOException("Owner already has " + MAX_CLIPS_PER_PLAYER + " dance clips");
        }
        if (canonical.bytes().length > MAX_BYTES_PER_PLAYER - ownedBytes(owner)) {
            throw new IOException("Owner dance storage limit of 10 MiB would be exceeded");
        }
        if (existingPhysical == null
            && canonical.bytes().length > MAX_GLOBAL_BYTES - storedBytes()) {
            throw new IOException("Dance library storage limit of 128 MiB would be exceeded");
        }

        Clip clip = new Clip(canonical.hash(), canonical.hash(), owner, cleanTitle,
            canonical.bytes().length, canonical.duration(), canonical.frames());
        Path target = clipPath(clip.hash());
        Path tempClip = null;
        Path tempIndex = null;
        boolean newPhysical = existingPhysical == null;
        try {
            if (newPhysical) {
                tempClip = Files.createTempFile(clipDir, "upload-", ".tmp");
                Files.write(tempClip, canonical.bytes());
            }
            List<Clip> prospective = allClips();
            prospective.add(clip);
            tempIndex = writeIndexTemp(prospective);
            if (newPhysical) {
                atomicReplace(tempClip, target);
                tempClip = null;
            }
            try {
                atomicReplace(tempIndex, indexFile);
                tempIndex = null;
            } catch (IOException e) {
                if (newPhysical) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException cleanup) {
                        e.addSuppressed(cleanup);
                    }
                }
                throw e;
            }
            physical.putIfAbsent(clip.hash(), new Physical(clip.bytes(), clip.duration(),
                clip.frames()));
            ownersByHash.computeIfAbsent(clip.hash(), ignored -> new LinkedHashMap<>())
                .put(owner, cleanTitle);
            return clip;
        } finally {
            deleteTemp(tempClip);
            if (tempIndex != null) {
                deleteTemp(tempIndex);
            }
        }
    }

    /** Read and hash-verify complete stored canonical bytes. Intended for worker threads. */
    public synchronized byte[] read(String hash) throws IOException {
        Physical clip = findPhysical(hash);
        if (clip == null) {
            throw new IOException("Unknown dance clip: " + hash);
        }
        Path file = clipPath(hash);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Dance clip file is missing: " + hash);
        }
        long size = Files.size(file);
        if (size != clip.bytes()) {
            throw new IOException("Dance clip size changed on disk: expected " + clip.bytes()
                + " bytes, found " + size);
        }
        byte[] data = Files.readAllBytes(file);
        if (data.length != clip.bytes()) {
            throw new IOException("Dance clip size changed while reading");
        }
        if (!sha256(data).equals(hash)) {
            throw new IOException("Dance clip content hash does not match the index: " + hash);
        }
        return data;
    }

    /**
     * Rename one registration. An owner renames their own title; an operator without a
     * registration for the hash renames its primary (oldest) registration.
     */
    public synchronized Clip rename(String hash, UUID actor, boolean operator, String title)
        throws IOException {
        Map<UUID, String> owners = ownersByHash.get(hash);
        if (owners == null || owners.isEmpty()) {
            throw new IOException("Unknown dance clip");
        }
        UUID target;
        if (owners.containsKey(actor)) {
            target = actor;
        } else if (operator) {
            target = owners.keySet().iterator().next();
        } else {
            throw new IOException("Only the owner or an operator can rename this clip");
        }
        String clean;
        try {
            clean = cleanTitle(title);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        Clip existing = makeClip(hash, target, owners.get(target));
        Clip renamed = new Clip(existing.id(), existing.hash(), existing.owner(), clean,
            existing.bytes(), existing.duration(), existing.frames());
        List<Clip> prospective = allClips();
        prospective.replaceAll(clip -> clip.hash().equals(hash) && clip.owner().equals(target)
            ? renamed : clip);
        replaceIndex(prospective);
        owners.put(target, clean);
        return renamed;
    }

    /**
     * Delete a registration and safely reset only the affected owner's references to it.
     *
     * <p>An actor who owns the hash deletes just that registration, even if they are an operator.
     * An operator who has no registration for the hash deletes every registration and the
     * physical file (moderation). The file is retained while any other registration remains.</p>
     */
    public synchronized boolean delete(String hash, UUID actor, boolean operator) throws IOException {
        Map<UUID, String> owners = ownersByHash.get(hash);
        if (owners == null || owners.isEmpty()) {
            return false;
        }
        boolean actorOwned = owners.containsKey(actor);
        if (!actorOwned && !operator) {
            throw new IOException("Only the owner or an operator can delete this clip");
        }
        boolean deleteAll = !actorOwned;
        List<Clip> prospective = new ArrayList<>();
        for (Clip clip : allClips()) {
            if (!clip.hash().equals(hash)) {
                prospective.add(clip);
            } else if (!deleteAll && !clip.owner().equals(actor)) {
                prospective.add(clip);
            }
        }
        boolean lastRegistration = prospective.stream()
            .noneMatch(clip -> clip.hash().equals(hash));
        replaceIndex(prospective);

        if (deleteAll) {
            owners.clear();
        } else {
            owners.remove(actor);
        }
        if (lastRegistration) {
            ownersByHash.remove(hash);
            physical.remove(hash);
        }
        if (lastRegistration) {
            try {
                Files.deleteIfExists(clipPath(hash));
            } catch (IOException e) {
                // The index is already authoritative; a crash here leaves only an orphan file.
                LOGGER.warn("Could not delete dance clip file for {}", hash, e);
            }
        }
        if (clearReferences(deleteAll ? null : actor, hash)) {
            try {
                savePreferences();
            } catch (IOException e) {
                LOGGER.warn("Could not persist dance preferences after deleting {}", hash, e);
            }
        }
        return true;
    }

    /** Sanitized immutable preferences; a missing player receives the documented defaults. */
    public synchronized Prefs preferences(UUID player) {
        Objects.requireNonNull(player, "player");
        Prefs stored = preferences.get(player);
        Prefs safe = sanitize(player, stored == null ? Prefs.defaults() : stored);
        if (stored != null && !stored.equals(safe)) {
            preferences.put(player, safe);
            try {
                savePreferences();
            } catch (IOException e) {
                LOGGER.warn("Could not persist sanitized dance preferences for {}", player, e);
            }
        }
        return safe;
    }

    /** Empty id resets that channel to its documented default. */
    public synchronized Prefs setPreference(UUID player, Trigger trigger, String clipId)
        throws IOException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(trigger, "trigger");
        String selected = clipId == null || clipId.isBlank() ? trigger.defaultClip()
            : requireSelectable(player, clipId);
        Prefs next = preferences(player).with(trigger, selected);
        preferences.put(player, next);
        savePreferences();
        return next;
    }

    /** Empty id clears the slot. */
    public synchronized Prefs setSlot(UUID player, int slot, String clipId) throws IOException {
        Objects.requireNonNull(player, "player");
        if (slot < 0 || slot >= Prefs.SLOT_COUNT) {
            throw new IOException("Slot index out of range: " + slot);
        }
        String selected = clipId == null || clipId.isBlank() ? Prefs.EMPTY
            : requireSelectable(player, clipId);
        Prefs next = preferences(player).withSlot(slot, selected);
        preferences.put(player, next);
        savePreferences();
        return next;
    }

    /** Builtin ids always pass; uploaded ids must be registered by the player. */
    public synchronized boolean selectable(UUID player, String clipId) {
        if (isBuiltin(clipId)) {
            return true;
        }
        Map<UUID, String> owners = findOwners(clipId);
        return owners != null && owners.containsKey(player);
    }

    private String requireSelectable(UUID player, String clipId) throws IOException {
        if (isBuiltin(clipId)) {
            return clipId;
        }
        Map<UUID, String> owners = findOwners(clipId);
        if (owners == null) {
            throw new IOException("Unknown dance clip");
        }
        if (!owners.containsKey(player)) {
            throw new IOException("Only the owner may select this uploaded dance");
        }
        return clipId;
    }

    private Prefs sanitize(UUID player, Prefs dirty) {
        List<String> slots = new ArrayList<>(Prefs.SLOT_COUNT);
        for (String slot : dirty.slots()) {
            slots.add(safeSelection(player, slot, Prefs.EMPTY));
        }
        return new Prefs(
            safeSelection(player, dirty.music(), Trigger.MUSIC.defaultClip()),
            safeSelection(player, dirty.revival(), Trigger.REVIVAL.defaultClip()),
            safeSelection(player, dirty.maidMusic(), Trigger.MAID_MUSIC.defaultClip()),
            safeSelection(player, dirty.animalMusic(), Trigger.ANIMAL_MUSIC.defaultClip()),
            slots);
    }

    private String safeSelection(UUID player, String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        if (isBuiltin(value)) {
            return value;
        }
        Map<UUID, String> owners = findOwners(value);
        return owners != null && owners.containsKey(player) ? value : fallback;
    }

    /** Clear references for one owner, or for every player when {@code owner} is null. */
    private boolean clearReferences(UUID owner, String hash) {
        boolean changed = false;
        for (Map.Entry<UUID, Prefs> entry : preferences.entrySet()) {
            if (owner != null && !entry.getKey().equals(owner)) {
                continue;
            }
            Prefs before = entry.getValue();
            Prefs after = new Prefs(
                hash.equals(before.music()) ? Trigger.MUSIC.defaultClip() : before.music(),
                hash.equals(before.revival()) ? Trigger.REVIVAL.defaultClip() : before.revival(),
                hash.equals(before.maidMusic()) ? Trigger.MAID_MUSIC.defaultClip() : before.maidMusic(),
                hash.equals(before.animalMusic()) ? Trigger.ANIMAL_MUSIC.defaultClip()
                    : before.animalMusic(),
                before.slots().stream().map(slot -> hash.equals(slot) ? Prefs.EMPTY : slot).toList());
            if (!before.equals(after)) {
                entry.setValue(after);
                changed = true;
            }
        }
        return changed;
    }

    private Map<UUID, String> findOwners(String hash) {
        if (!isStrictHash(hash)) {
            return null;
        }
        return ownersByHash.get(hash);
    }

    private Physical findPhysical(String hash) {
        if (!isStrictHash(hash)) {
            return null;
        }
        return physical.get(hash);
    }

    private Clip makeClip(String hash, UUID owner, String title) {
        Physical clip = physical.get(hash);
        if (clip == null) {
            return null;
        }
        return new Clip(hash, hash, owner, title, clip.bytes(), clip.duration(), clip.frames());
    }

    private List<Clip> allClips() {
        List<Clip> result = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : ownersByHash.entrySet()) {
            for (Map.Entry<UUID, String> owner : entry.getValue().entrySet()) {
                Clip clip = makeClip(entry.getKey(), owner.getKey(), owner.getValue());
                if (clip != null) {
                    result.add(clip);
                }
            }
        }
        return result;
    }

    private Path clipPath(String hash) {
        return clipDir.resolve(hash + ".json");
    }

    private static boolean matches(Physical physical, Canonical canonical) {
        return physical.bytes() == canonical.bytes().length
            && Float.compare(physical.duration(), canonical.duration()) == 0
            && physical.frames() == canonical.frames();
    }

    /**
     * Public pure validator shared by the server library and clients. It never touches the world
     * directory and never creates a {@link DanceLibrary} instance.
     *
     * @throws IOException when the bytes are not canonical schema-1 penguin animation JSON
     */
    public static Canonical canonicalize(byte[] raw) throws IOException {
        if (raw == null || raw.length == 0) {
            throw new IOException("Dance file is empty");
        }
        if (raw.length > MAX_FILE_BYTES) {
            throw new IOException("Dance file exceeds 2 MiB");
        }
        String text = decodeUtf8(raw);
        String lead = text.stripLeading();
        if (lead.isEmpty() || lead.charAt(0) != '{') {
            throw new IOException("Only canonical penguin animation JSON is accepted");
        }
        strictScanJson(text);
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IOException("Animation root must be a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Malformed penguin animation JSON", e);
        }

        JsonElement framesElement = root.get("frames");
        if (framesElement == null || !framesElement.isJsonArray()) {
            throw new IOException("Animation has no frame array");
        }
        JsonArray frames = framesElement.getAsJsonArray();
        if (frames.size() < 2 || frames.size() > MAX_FRAMES) {
            throw new IOException("Animation frame count must be 2.." + MAX_FRAMES);
        }
        JsonArray zero = new JsonArray();
        zero.add(0);
        zero.add(0);
        zero.add(0);
        for (int f = 0; f < frames.size(); f++) {
            JsonElement frameElement = frames.get(f);
            if (frameElement == null || !frameElement.isJsonObject()) {
                throw new IOException("Animation frame " + f + " is not an object");
            }
            JsonElement translationsElement = frameElement.getAsJsonObject().get("translations");
            if (translationsElement == null || !translationsElement.isJsonArray()) {
                throw new IOException("Animation frame " + f + " has no translations");
            }
            JsonArray translations = translationsElement.getAsJsonArray();
            if (translations.size() != PenguinRig.COUNT) {
                throw new IOException("Animation frame " + f + " must contain 16 translations");
            }
            JsonElement rootElement = translations.get(0);
            if (rootElement == null || !rootElement.isJsonArray()) {
                throw new IOException("Animation frame " + f + " root translation is not an array");
            }
            JsonArray rootTranslation = rootElement.getAsJsonArray();
            if (rootTranslation.size() != 3) {
                throw new IOException("Animation frame " + f + " root translation must have 3 values");
            }
            for (int axis = 0; axis < 3; axis++) {
                JsonElement value = rootTranslation.get(axis);
                if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                    throw new IOException("Animation frame " + f
                        + " root translation must contain only numbers");
                }
                double number;
                try {
                    number = value.getAsDouble();
                } catch (RuntimeException e) {
                    throw new IOException("Animation frame " + f
                        + " root translation is not a finite number", e);
                }
                if (!Double.isFinite(number)) {
                    throw new IOException("Animation frame " + f
                        + " root translation is not a finite number");
                }
            }
            // Imported dances never carry root motion. Normalize only after the source root vector
            // was proven to be a finite three-component numeric array.
            translations.set(0, zero.deepCopy());
        }

        String canonicalText = CLIP_GSON.toJson(root);
        byte[] canonicalBytes = canonicalText.getBytes(StandardCharsets.UTF_8);
        if (canonicalBytes.length > MAX_FILE_BYTES) {
            throw new IOException("Canonical dance file exceeds 2 MiB");
        }
        PenguinAnimationClip clip;
        try (Reader reader = new StringReader(canonicalText)) {
            clip = PenguinAnimationClip.parse(reader);
        } catch (RuntimeException e) {
            throw new IOException("Invalid penguin animation", e);
        }
        if (!clip.sameRigAs(referenceClip())) {
            throw new IOException("Animation bind does not match the canonical penguin rig");
        }
        if (clip.totalDuration() > MAX_SECONDS) {
            throw new IOException("Animation duration exceeds 30 seconds");
        }
        return new Canonical(canonicalBytes, sha256(canonicalBytes), clip.totalDuration(),
            frames.size(), clip);
    }

    private static PenguinAnimationClip referenceClip() throws IOException {
        PenguinAnimationClip cached = referenceClipCache;
        if (cached != null) {
            return cached;
        }
        synchronized (DanceLibrary.class) {
            cached = referenceClipCache;
            if (cached == null) {
                cached = loadReferenceClip();
                referenceClipCache = cached;
            }
            return cached;
        }
    }

    private static PenguinAnimationClip loadReferenceClip() throws IOException {
        var stream = DanceLibrary.class.getResourceAsStream(TAUNT_RESOURCE);
        if (stream == null) {
            throw new IOException("Canonical taunt animation resource is missing");
        }
        try (var input = stream; Reader reader = new java.io.InputStreamReader(
            input, StandardCharsets.UTF_8)) {
            return PenguinAnimationClip.parse(reader);
        } catch (RuntimeException e) {
            throw new IOException("Canonical taunt animation is invalid", e);
        }
    }

    private void loadIndex() {
        if (!Files.exists(indexFile)) {
            return;
        }
        if (!Files.isRegularFile(indexFile)) {
            throw corrupt("dance index is not a regular file: " + indexFile, null);
        }
        JsonObject json;
        try {
            String text = Files.readString(indexFile, StandardCharsets.UTF_8);
            strictScanJson(text);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("root is not an object");
            }
            json = parsed.getAsJsonObject();
        } catch (IOException e) {
            throw corrupt("cannot read " + indexFile, e);
        } catch (RuntimeException e) {
            throw corrupt("invalid JSON in " + indexFile, e);
        }
        int schema = requireInt(json, "schema");
        if (schema != SCHEMA) {
            throw corrupt("unsupported dance index schema " + schema, null);
        }
        JsonElement clipsElement = json.get("clips");
        if (clipsElement == null || !clipsElement.isJsonArray()) {
            throw corrupt("dance index has no clips array", null);
        }
        long total = 0L;
        for (JsonElement element : clipsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw corrupt("dance index entry is not an object", null);
            }
            Clip clip = parseClip(element.getAsJsonObject());
            Physical candidate = new Physical(clip.bytes(), clip.duration(), clip.frames());
            Physical existing = physical.get(clip.hash());
            if (existing == null) {
                physical.put(clip.hash(), candidate);
                total += clip.bytes();
                if (total > MAX_GLOBAL_BYTES) {
                    throw corrupt("dance index exceeds the global storage cap", null);
                }
            } else if (!existing.equals(candidate)) {
                throw corrupt("dance index has conflicting metadata for " + clip.hash(), null);
            }
            Map<UUID, String> owners = ownersByHash.computeIfAbsent(clip.hash(),
                ignored -> new LinkedHashMap<>());
            String previous = owners.putIfAbsent(clip.owner(), clip.title());
            if (previous != null && !previous.equals(clip.title())) {
                throw corrupt("dance index has conflicting registrations for " + clip.hash()
                    + " / " + clip.owner(), null);
            }
        }
    }

    private Clip parseClip(JsonObject json) {
        try {
            String id = requireString(json, "id");
            String hash = requireString(json, "hash");
            UUID owner = UUID.fromString(requireString(json, "owner"));
            String title = requireString(json, "title");
            int bytes = requireInt(json, "bytes");
            float duration = (float) requireDouble(json, "duration");
            int frames = requireInt(json, "frames");
            Clip clip = new Clip(id, hash, owner, title, bytes, duration, frames);
            Path file = clipPath(hash).normalize();
            if (!file.startsWith(clipDir) || !Files.isRegularFile(file)) {
                throw corrupt("dance clip file is missing: " + hash, null);
            }
            if (Files.size(file) != clip.bytes()) {
                throw corrupt("dance clip file size does not match the index: " + hash, null);
            }
            return clip;
        } catch (IOException e) {
            throw corrupt("cannot inspect dance clip file", e);
        } catch (RuntimeException e) {
            throw corrupt("invalid dance clip metadata", e);
        }
    }

    private void loadPreferences() {
        if (!Files.exists(prefsFile)) {
            return;
        }
        if (!Files.isRegularFile(prefsFile)) {
            LOGGER.warn("Dance preferences path is not a regular file; using defaults: {}", prefsFile);
            return;
        }
        try {
            String text = Files.readString(prefsFile, StandardCharsets.UTF_8);
            strictScanJson(text);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("root is not an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            int schema = requireInt(root, "schema");
            if (schema != SCHEMA) {
                throw new IllegalArgumentException("unsupported preferences schema " + schema);
            }
            JsonElement playersElement = root.get("players");
            if (playersElement == null || !playersElement.isJsonObject()) {
                throw new IllegalArgumentException("players is not an object");
            }
            for (Map.Entry<String, JsonElement> entry : playersElement.getAsJsonObject().entrySet()) {
                UUID player = UUID.fromString(entry.getKey());
                if (!entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("player preferences are not an object");
                }
                preferences.put(player, parsePrefs(entry.getValue().getAsJsonObject()));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read dance preferences {}; using defaults", prefsFile, e);
            preferences.clear();
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dance preferences {}; using defaults", prefsFile, e);
            preferences.clear();
        }
    }

    private Prefs parsePrefs(JsonObject json) {
        String music = requireString(json, "music");
        String revival = requireString(json, "revival");
        String maidMusic = requireString(json, "maid_music");
        // Schema 1 predates the independent animal channel: old animals used the stored music
        // choice, so migrate from that field. A present but malformed field is never repaired.
        String animalMusic = json.has("animal_music") ? requireString(json, "animal_music") : music;
        JsonElement slotsElement = json.get("slots");
        if (slotsElement == null || !slotsElement.isJsonArray()) {
            throw new IllegalArgumentException("slots is not an array");
        }
        JsonArray slotsArray = slotsElement.getAsJsonArray();
        if (slotsArray.size() != Prefs.SLOT_COUNT) {
            throw new IllegalArgumentException("wrong slot count");
        }
        List<String> slots = new ArrayList<>(Prefs.SLOT_COUNT);
        for (JsonElement slot : slotsArray) {
            if (!slot.isJsonPrimitive() || !slot.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("slot is not a string");
            }
            slots.add(slot.getAsString());
        }
        return new Prefs(music, revival, maidMusic, animalMusic, slots);
    }

    private void savePreferences() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, Prefs> entry : preferences.entrySet()) {
            Prefs prefs = entry.getValue();
            JsonObject item = new JsonObject();
            item.addProperty("music", prefs.music());
            item.addProperty("revival", prefs.revival());
            item.addProperty("maid_music", prefs.maidMusic());
            item.addProperty("animal_music", prefs.animalMusic());
            JsonArray slots = new JsonArray();
            for (String slot : prefs.slots()) {
                slots.add(slot);
            }
            item.add("slots", slots);
            players.add(entry.getKey().toString(), item);
        }
        json.add("players", players);
        Path temp = Files.createTempFile(root, "preferences-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
            atomicReplace(temp, prefsFile);
        } finally {
            deleteTemp(temp);
        }
    }

    private void replaceIndex(List<Clip> clips) throws IOException {
        Path temp = writeIndexTemp(clips);
        try {
            atomicReplace(temp, indexFile);
        } finally {
            deleteTemp(temp);
        }
    }

    private Path writeIndexTemp(List<Clip> clips) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        JsonArray array = new JsonArray();
        for (Clip clip : clips) {
            JsonObject item = new JsonObject();
            item.addProperty("id", clip.id());
            item.addProperty("hash", clip.hash());
            item.addProperty("owner", clip.owner().toString());
            item.addProperty("title", clip.title());
            item.addProperty("bytes", clip.bytes());
            item.addProperty("duration", clip.duration());
            item.addProperty("frames", clip.frames());
            array.add(item);
        }
        json.add("clips", array);
        Path temp = Files.createTempFile(root, "index-", ".tmp");
        Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
        return temp;
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
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
            LOGGER.warn("Could not delete temporary dance file {}", temp, e);
        }
    }

    private static String decodeUtf8(byte[] raw) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Dance file is not valid UTF-8", e);
        }
    }

    /**
     * Strict, bounded pre-scan so Gson never sees duplicate object keys or arbitrarily deep JSON.
     * The scanner accepts only RFC 8259 tokens (no comments, unquoted names, single quotes, NaN or
     * Infinity) and stops after {@link #MAX_JSON_TOKENS} tokens.
     */
    private static void strictScanJson(String text) throws IOException {
        new StrictJsonReader(text).scanDocument();
    }

    private static final class StrictJsonReader {
        private final String text;
        private int index;
        private long tokens;

        private StrictJsonReader(String text) {
            this.text = text;
        }

        private void scanDocument() throws IOException {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("empty JSON");
            }
            scanValue(0);
            skipWhitespace();
            if (index != text.length()) {
                throw error("trailing data after the JSON value");
            }
        }

        private void scanValue(int depth) throws IOException {
            if (depth > MAX_JSON_DEPTH) {
                throw error("JSON nesting exceeds " + MAX_JSON_DEPTH);
            }
            countToken();
            skipWhitespace();
            if (index >= text.length()) {
                throw error("unexpected end of JSON");
            }
            char c = text.charAt(index);
            if ((c == '{' || c == '[') && depth >= MAX_JSON_DEPTH) {
                throw error("JSON nesting exceeds " + MAX_JSON_DEPTH);
            }
            switch (c) {
                case '{' -> scanObject(depth + 1);
                case '[' -> scanArray(depth + 1);
                case '"' -> scanString();
                case 't' -> expectLiteral("true");
                case 'f' -> expectLiteral("false");
                case 'n' -> expectLiteral("null");
                default -> {
                    if (c == '-' || isDigit(c)) {
                        scanNumber();
                    } else {
                        throw error("unexpected character '" + c + "'");
                    }
                }
            }
        }

        private void scanObject(int depth) throws IOException {
            index++;
            skipWhitespace();
            if (consume('}')) {
                return;
            }
            Set<String> names = new HashSet<>();
            while (true) {
                skipWhitespace();
                if (index >= text.length() || text.charAt(index) != '"') {
                    throw error("object key must be a quoted string");
                }
                countToken();
                String name = scanString();
                if (!names.add(name)) {
                    throw error("duplicate object key '" + name + "'");
                }
                skipWhitespace();
                expect(':');
                scanValue(depth);
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                if (consume('}')) {
                    return;
                }
                throw error("expected ',' or '}' in object");
            }
        }

        private void scanArray(int depth) throws IOException {
            index++;
            skipWhitespace();
            if (consume(']')) {
                return;
            }
            while (true) {
                scanValue(depth);
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                if (consume(']')) {
                    return;
                }
                throw error("expected ',' or ']' in array");
            }
        }

        private String scanString() throws IOException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return value.toString();
                }
                if (c < 0x20) {
                    throw error("unescaped control character in string");
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= text.length()) {
                    throw error("unterminated escape sequence");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw error("truncated unicode escape");
                        }
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int digit = Character.digit(text.charAt(index++), 16);
                            if (digit < 0) {
                                throw error("invalid unicode escape");
                            }
                            code = (code << 4) | digit;
                        }
                        value.append((char) code);
                    }
                    default -> throw error("invalid escape sequence");
                }
            }
            throw error("unterminated string");
        }

        private void scanNumber() throws IOException {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            if (index >= text.length()) {
                throw error("truncated number");
            }
            char c = text.charAt(index);
            if (c == '0') {
                index++;
            } else if (c >= '1' && c <= '9') {
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            } else {
                throw error("invalid number");
            }
            if (index < text.length() && text.charAt(index) == '.') {
                index++;
                if (index >= text.length() || !isDigit(text.charAt(index))) {
                    throw error("invalid fraction");
                }
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e'
                || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+'
                    || text.charAt(index) == '-')) {
                    index++;
                }
                if (index >= text.length() || !isDigit(text.charAt(index))) {
                    throw error("invalid exponent");
                }
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index == start) {
                throw error("invalid number");
            }
        }

        private void expectLiteral(String literal) throws IOException {
            if (!text.startsWith(literal, index)) {
                throw error("invalid literal");
            }
            index += literal.length();
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            return index < text.length() ? text.charAt(index) : '\0';
        }

        private boolean consume(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) throws IOException {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private void countToken() throws IOException {
            if (++tokens > MAX_JSON_TOKENS) {
                throw error("JSON token budget exceeded");
            }
        }

        private IOException error(String message) {
            return new IOException("Invalid JSON at character " + index + ": " + message);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }

    public static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static boolean isStrictHash(String id) {
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

    private static String cleanTitle(String title) {
        Objects.requireNonNull(title, "title");
        String cleaned = title.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Dance title must not be empty");
        }
        if (cleaned.length() > MAX_TITLE_CHARS) {
            throw new IllegalArgumentException("Dance title exceeds " + MAX_TITLE_CHARS + " characters");
        }
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            int type = Character.getType(c);
            if (c < 0x20 || c == 0x7f || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException("Dance title must be a single clean line");
            }
        }
        return cleaned;
    }

    private static int requireInt(JsonObject json, String name) {
        long value = requireLong(json, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("field '" + name + "' is outside the int range");
        }
        return (int) value;
    }

    private static double requireDouble(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric field '" + name + "'");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("field '" + name + "' is not finite");
        }
        return value;
    }

    private static long requireLong(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing numeric field '" + name + "'");
        }
        return element.getAsLong();
    }

    private static String requireString(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing string field '" + name + "'");
        }
        return element.getAsString();
    }

    private static IllegalStateException corrupt(String message, Throwable cause) {
        String full = "Corrupt dance library: " + message;
        LOGGER.error(full);
        return new IllegalStateException(full, cause);
    }
}
