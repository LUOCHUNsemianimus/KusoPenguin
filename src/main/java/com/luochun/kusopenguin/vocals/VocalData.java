package com.luochun.kusopenguin.vocals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Persistent vocal metadata stored on an instrument item.
 *
 * <p>All state lives in the item's {@link DataComponents#CUSTOM_DATA} under the single
 * {@code KusoVocals} compound. Every write copies the existing custom tag first, so unrelated
 * components and unrelated custom-data keys survive. A configured stack carries its settings when
 * it is given to a maid or moved between inventories; there is no global or per-entity server map.
 *
 * <p>The contract is deliberately small: read the enabled flag and bindings, bind or remove one
 * melody/resource pair, and toggle playback. {@link #bind(ItemStack, Binding)} enables the feature;
 * removal and {@link #setEnabled(ItemStack, boolean)} never change any other binding.
 */
public final class VocalData {
    public static final int MAX_BINDINGS = 16;
    public static final int MAX_RESOURCE_ID_LENGTH = 256;
    public static final int MIN_OFFSET_MS = -30_000;
    public static final int MAX_OFFSET_MS = 30_000;
    public static final float MAX_GAIN = 2.0F;
    /** Sentinel melody id for the standalone microphone; it is not an Immersive Melodies hash. */
    public static final String MICROPHONE_MELODY = "kuso_penguin:microphone";
    public static final int MIN_MICROPHONE_DURATION_MS = 1;
    public static final int MAX_MICROPHONE_DURATION_MS = 300_000;

    private static final String ROOT_TAG = "KusoVocals";
    private static final String ENABLED_TAG = "Enabled";
    private static final String BINDINGS_TAG = "Bindings";
    private static final String MELODY_ID_TAG = "MelodyId";
    private static final String MELODY_HASH_TAG = "MelodyHash";
    private static final String TRACK_ID_TAG = "TrackId";
    private static final String OFFSET_MS_TAG = "OffsetMs";
    private static final String GAIN_TAG = "Gain";
    private static final String MICROPHONE_DURATION_TAG = "MicrophoneDurationMs";

    private VocalData() {
    }

    /**
     * One melody fingerprint plus the pre-recorded audio track that belongs to it. The melody and
     * track are both content-addressed; {@code melodyId} is only the lookup key.
     */
    public record Binding(String melodyId, String melodyHash, String trackId, int offsetMs,
                          float gain) {
    }

    /** Explicit opt-in flag; false when the stack has no vocal data. */
    public static boolean enabled(ItemStack stack) {
        CompoundTag root = vocalTag(stack, false);
        return root != null && root.getBoolean(ENABLED_TAG);
    }

    /** Writes only the enabled flag, preserving every other custom-data tag and binding. */
    public static void setEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag root = vocalTag(stack, true);
        if (root.getBoolean(ENABLED_TAG) == enabled) {
            return;
        }
        root.putBoolean(ENABLED_TAG, enabled);
        writeVocalTag(stack, root);
    }

    /**
     * Reads at most {@link #MAX_BINDINGS} persisted entries. Entries with bad ids, hashes, offsets
     * or gains are skipped instead of being surfaced to callers.
     */
    public static List<Binding> bindings(ItemStack stack) {
        CompoundTag root = vocalTag(stack, false);
        if (root == null) {
            return List.of();
        }
        ListTag stored = root.getList(BINDINGS_TAG, Tag.TAG_COMPOUND);
        int readLimit = Math.min(stored.size(), MAX_BINDINGS);
        List<Binding> result = new ArrayList<>(readLimit);
        for (int i = 0; i < readLimit; i++) {
            Binding binding = readBinding(stored.getCompound(i));
            if (binding != null) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    /** Exact resource-id plus melody-hash lookup, or null when absent or persisted invalid. */
    public static Binding binding(ItemStack stack, String melodyId, String melodyHash) {
        if (melodyId == null || melodyHash == null) {
            return null;
        }
        for (Binding binding : bindings(stack)) {
            if (binding.melodyId().equals(melodyId)
                    && binding.melodyHash().equals(melodyHash)) {
                return binding;
            }
        }
        return null;
    }

    /**
     * The one active microphone clip, or null when the microphone is unconfigured. A microphone
     * binding uses the sentinel melody id and stores the audio hash in both hash fields so it can
     * never be mistaken for a MIDI melody fingerprint.
     */
    public static Binding microphoneBinding(ItemStack stack) {
        for (Binding binding : bindings(stack)) {
            if (MICROPHONE_MELODY.equals(binding.melodyId())
                    && binding.melodyHash().equals(binding.trackId())) {
                return binding;
            }
        }
        return null;
    }

    /**
     * Stored microphone clip length in milliseconds. Returns 0 when no microphone binding exists;
     * a stored value is bounded to {@code [1, 300000]} for the next client stage.
     */
    public static int microphoneDurationMs(ItemStack stack) {
        if (microphoneBinding(stack) == null) {
            return 0;
        }
        CompoundTag root = vocalTag(stack, false);
        if (root == null) {
            return 0;
        }
        int duration = root.getInt(MICROPHONE_DURATION_TAG);
        if (duration <= 0) {
            return 0;
        }
        return Math.min(duration, MAX_MICROPHONE_DURATION_MS);
    }

    /**
     * Replaces the one active microphone clip and enables it. The audio hash is used as both the
     * melody-hash and track-id field, the offset is always zero, and unrelated bindings/tags and
     * other components are preserved. Invalid input is rejected before any write.
     */
    public static void configureMicrophone(ItemStack stack, String trackId, int durationMs,
                                           float gain) {
        if (stack == null || stack.isEmpty() || !isLowerHex64(trackId)
                || durationMs < MIN_MICROPHONE_DURATION_MS
                || durationMs > MAX_MICROPHONE_DURATION_MS
                || !Float.isFinite(gain) || gain < 0.0F || gain > MAX_GAIN) {
            return;
        }
        Binding microphone = new Binding(MICROPHONE_MELODY, trackId, trackId, 0, gain);
        CompoundTag root = vocalTag(stack, true);
        ListTag rebuilt = new ListTag();
        boolean replaced = false;
        for (Binding existing : bindings(stack)) {
            if (MICROPHONE_MELODY.equals(existing.melodyId())) {
                if (!replaced) {
                    rebuilt.add(writeBinding(microphone));
                    replaced = true;
                }
            } else {
                rebuilt.add(writeBinding(existing));
            }
        }
        if (!replaced) {
            if (rebuilt.size() >= MAX_BINDINGS) {
                return;
            }
            rebuilt.add(writeBinding(microphone));
        }
        root.put(BINDINGS_TAG, rebuilt);
        root.putInt(MICROPHONE_DURATION_TAG, durationMs);
        root.putBoolean(ENABLED_TAG, true);
        writeVocalTag(stack, root);
    }

    /** Removes the microphone binding and duration while preserving unrelated bindings/tags. */
    public static void clearMicrophone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag root = vocalTag(stack, false);
        if (root == null) {
            return;
        }
        ListTag rebuilt = new ListTag();
        for (Binding existing : bindings(stack)) {
            if (!MICROPHONE_MELODY.equals(existing.melodyId())) {
                rebuilt.add(writeBinding(existing));
            }
        }
        root.put(BINDINGS_TAG, rebuilt);
        root.remove(MICROPHONE_DURATION_TAG);
        writeVocalTag(stack, root);
    }

    /**
     * Inserts or replaces the binding with the same resource id and melody hash, then enables
     * vocals. Invalid bindings and a seventeenth distinct binding are ignored without touching the
     * existing data. Replacing an existing pair keeps the list size and order unchanged.
     */
    public static void bind(ItemStack stack, Binding binding) {
        if (stack == null || stack.isEmpty() || !valid(binding)) {
            return;
        }
        CompoundTag root = vocalTag(stack, true);
        List<Binding> current = bindings(stack);
        ListTag rebuilt = new ListTag();
        boolean replaced = false;
        for (Binding existing : current) {
            if (existing.melodyId().equals(binding.melodyId())
                    && existing.melodyHash().equals(binding.melodyHash())) {
                rebuilt.add(writeBinding(binding));
                replaced = true;
            } else {
                rebuilt.add(writeBinding(existing));
            }
        }
        if (!replaced) {
            if (current.size() >= MAX_BINDINGS) {
                return;
            }
            rebuilt.add(writeBinding(binding));
        }
        root.put(BINDINGS_TAG, rebuilt);
        root.putBoolean(ENABLED_TAG, true);
        writeVocalTag(stack, root);
    }

    /**
     * Removes exactly the resource-id plus melody-hash pair. Other bindings are preserved and the
     * enabled flag is deliberately left unchanged.
     */
    public static void remove(ItemStack stack, String melodyId, String melodyHash) {
        if (stack == null || stack.isEmpty() || melodyId == null || melodyHash == null) {
            return;
        }
        CompoundTag root = vocalTag(stack, false);
        if (root == null) {
            return;
        }
        List<Binding> current = bindings(stack);
        ListTag rebuilt = new ListTag();
        for (Binding existing : current) {
            if (!existing.melodyId().equals(melodyId)
                    || !existing.melodyHash().equals(melodyHash)) {
                rebuilt.add(writeBinding(existing));
            }
        }
        root.put(BINDINGS_TAG, rebuilt);
        writeVocalTag(stack, root);
    }

    /** True when every field is within the documented persisted bounds. */
    public static boolean valid(Binding binding) {
        return binding != null
                && validResourceId(binding.melodyId())
                && isLowerHex64(binding.melodyHash())
                && isLowerHex64(binding.trackId())
                && binding.offsetMs() >= MIN_OFFSET_MS
                && binding.offsetMs() <= MAX_OFFSET_MS
                && Float.isFinite(binding.gain())
                && binding.gain() >= 0.0F
                && binding.gain() <= MAX_GAIN;
    }

    private static CompoundTag vocalTag(ItemStack stack, boolean create) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = data == null ? new CompoundTag() : data.copyTag();
        if (root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return root.getCompound(ROOT_TAG);
        }
        if (!create) {
            return null;
        }
        CompoundTag created = new CompoundTag();
        root.put(ROOT_TAG, created);
        return created;
    }

    private static void writeVocalTag(ItemStack stack, CompoundTag vocalTag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = data == null ? new CompoundTag() : data.copyTag();
        root.put(ROOT_TAG, vocalTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static Binding readBinding(CompoundTag tag) {
        Binding binding = new Binding(
                tag.getString(MELODY_ID_TAG),
                tag.getString(MELODY_HASH_TAG),
                tag.getString(TRACK_ID_TAG),
                tag.getInt(OFFSET_MS_TAG),
                tag.getFloat(GAIN_TAG));
        return valid(binding) ? binding : null;
    }

    private static CompoundTag writeBinding(Binding binding) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MELODY_ID_TAG, binding.melodyId());
        tag.putString(MELODY_HASH_TAG, binding.melodyHash());
        tag.putString(TRACK_ID_TAG, binding.trackId());
        tag.putInt(OFFSET_MS_TAG, binding.offsetMs());
        tag.putFloat(GAIN_TAG, binding.gain());
        return tag;
    }

    private static boolean validResourceId(String id) {
        return id != null
                && !id.isEmpty()
                && id.length() <= MAX_RESOURCE_ID_LENGTH
                && ResourceLocation.tryParse(id) != null;
    }

    private static boolean isLowerHex64(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
