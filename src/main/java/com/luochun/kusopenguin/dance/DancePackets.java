package com.luochun.kusopenguin.dance;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded wire records for the custom dance library. Every string, collection and byte array is
 * bounded on encode and checked before decode allocation. Numeric fields in a request are
 * advisory until {@link DanceServer} validates them; no codec check authorizes storage.
 */
public final class DancePackets {
    public static final int MAX_CLIP_ID_CHARS = DanceLibrary.MAX_CLIP_ID_CHARS;
    public static final int MAX_TITLE_CHARS = DanceLibrary.MAX_TITLE_CHARS;
    public static final int MAX_MESSAGE_CHARS = 256;
    public static final int MAX_CATALOG_CLIPS = DanceLibrary.MAX_CLIPS_PER_PLAYER;
    public static final int MAX_BUILTINS = 4;
    public static final int MAX_CHUNK = 16 * 1024;

    private DancePackets() {
    }

    /** Closed action set shared by requests, control messages and replies. */
    public enum Action {
        LIST, BEGIN_UPLOAD, END_UPLOAD, CANCEL_UPLOAD, DOWNLOAD, DELETE, RENAME,
        PLAY, STOP, SET_PREF, SET_SLOT
    }

    private static boolean isRequest(Action action) {
        return action == Action.LIST || action == Action.BEGIN_UPLOAD
            || action == Action.END_UPLOAD || action == Action.CANCEL_UPLOAD
            || action == Action.DOWNLOAD || action == Action.DELETE || action == Action.RENAME;
    }

    private static boolean isControl(Action action) {
        return action == Action.PLAY || action == Action.STOP || action == Action.SET_PREF
            || action == Action.SET_SLOT;
    }

    /** Client request/upload control. {@code totalBytes} and {@code expectedHash} are advisory. */
    public record Request(Action action, UUID ticket, String clipId, String title, int totalBytes,
                          String expectedHash) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action());
                buf.writeUUID(packet.ticket());
                writeString(buf, packet.clipId(), MAX_CLIP_ID_CHARS);
                writeString(buf, packet.title(), MAX_TITLE_CHARS);
                buf.writeInt(packet.totalBytes());
                writeString(buf, packet.expectedHash(), MAX_CLIP_ID_CHARS);
            },
            buf -> decode(() -> new Request(buf.readEnum(Action.class), buf.readUUID(),
                readString(buf, MAX_CLIP_ID_CHARS), readString(buf, MAX_TITLE_CHARS),
                buf.readInt(), readString(buf, MAX_CLIP_ID_CHARS))));

        public Request {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            if (!isRequest(action)) {
                throw new IllegalArgumentException("Not a request action: " + action);
            }
            clipId = boundedString(clipId, MAX_CLIP_ID_CHARS, "clipId");
            title = boundedString(title, MAX_TITLE_CHARS, "title");
            expectedHash = boundedString(expectedHash, MAX_CLIP_ID_CHARS, "expectedHash");
            if (!expectedHash.isEmpty() && !DanceLibrary.isStrictHash(expectedHash)) {
                throw new IllegalArgumentException("expectedHash must be a lowercase SHA-256");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One bounded canonical-JSON chunk for upload or download. */
    public record Chunk(UUID ticket, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.ticket());
                buf.writeVarInt(packet.offset());
                buf.writeByteArray(packet.data());
            },
            buf -> decode(() -> new Chunk(buf.readUUID(), buf.readVarInt(),
                buf.readByteArray(MAX_CHUNK))));

        public Chunk {
            Objects.requireNonNull(ticket, "ticket");
            Objects.requireNonNull(data, "data");
            if (data.length > MAX_CHUNK) {
                throw new IllegalArgumentException("Dance chunk exceeds " + MAX_CHUNK + " bytes");
            }
            if (offset < 0) {
                throw new IllegalArgumentException("Negative dance chunk offset");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server acknowledgement for a request, control or upload progress step. */
    public record Reply(Action action, UUID ticket, boolean success, String message, String clipId,
                        int totalBytes) implements CustomPacketPayload {
        public static final Type<Reply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action());
                buf.writeUUID(packet.ticket());
                buf.writeBoolean(packet.success());
                writeString(buf, packet.message(), MAX_MESSAGE_CHARS);
                writeString(buf, packet.clipId(), MAX_CLIP_ID_CHARS);
                buf.writeInt(packet.totalBytes());
            },
            buf -> decode(() -> new Reply(buf.readEnum(Action.class), buf.readUUID(),
                buf.readBoolean(), readString(buf, MAX_MESSAGE_CHARS),
                readString(buf, MAX_CLIP_ID_CHARS), buf.readInt())));

        public Reply {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            message = boundedString(message, MAX_MESSAGE_CHARS, "message");
            clipId = boundedString(clipId, MAX_CLIP_ID_CHARS, "clipId");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Own catalog plus metadata/preferences; never the whole server library. */
    public record Catalog(boolean open, Prefs prefs, List<DanceLibrary.Builtin> builtins,
                          List<DanceLibrary.Clip> clips) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.open());
                writePrefs(buf, packet.prefs());
                buf.writeVarInt(packet.builtins().size());
                for (DanceLibrary.Builtin builtin : packet.builtins()) {
                    writeBuiltin(buf, builtin);
                }
                buf.writeVarInt(packet.clips().size());
                for (DanceLibrary.Clip clip : packet.clips()) {
                    writeClip(buf, clip);
                }
            },
            buf -> decode(() -> {
                boolean open = buf.readBoolean();
                Prefs prefs = readPrefs(buf);
                int builtinCount = buf.readVarInt();
                if (builtinCount < 0 || builtinCount > MAX_BUILTINS) {
                    throw new DecoderException("Builtin count out of bounds: " + builtinCount);
                }
                List<DanceLibrary.Builtin> builtins = new ArrayList<>(builtinCount);
                for (int i = 0; i < builtinCount; i++) {
                    builtins.add(readBuiltin(buf));
                }
                int clipCount = buf.readVarInt();
                if (clipCount < 0 || clipCount > MAX_CATALOG_CLIPS) {
                    throw new DecoderException("Catalog clip count out of bounds: " + clipCount);
                }
                List<DanceLibrary.Clip> clips = new ArrayList<>(clipCount);
                for (int i = 0; i < clipCount; i++) {
                    clips.add(readClip(buf));
                }
                return new Catalog(open, prefs, builtins, clips);
            }));

        public Catalog {
            Objects.requireNonNull(prefs, "prefs");
            Objects.requireNonNull(builtins, "builtins");
            Objects.requireNonNull(clips, "clips");
            if (builtins.size() > MAX_BUILTINS) {
                throw new IllegalArgumentException("Catalog exceeds " + MAX_BUILTINS + " builtins");
            }
            if (clips.size() > MAX_CATALOG_CLIPS) {
                throw new IllegalArgumentException("Catalog exceeds " + MAX_CATALOG_CLIPS + " clips");
            }
            builtins = List.copyOf(builtins);
            clips = List.copyOf(clips);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Authoritative playback state. A unique session prevents a stale STOP from winning. */
    public record Playback(ResourceLocation dimension, UUID entity, UUID session, String clipId,
                           long startTick, Source source, boolean playing)
        implements CustomPacketPayload {
        public static final Type<Playback> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_playback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Playback> STREAM_CODEC =
            StreamCodec.of(
                (buf, packet) -> {
                    buf.writeResourceLocation(packet.dimension());
                    buf.writeUUID(packet.entity());
                    buf.writeUUID(packet.session());
                    writeString(buf, packet.clipId(), MAX_CLIP_ID_CHARS);
                    buf.writeLong(packet.startTick());
                    buf.writeEnum(packet.source());
                    buf.writeBoolean(packet.playing());
                },
                buf -> decode(() -> new Playback(buf.readResourceLocation(), buf.readUUID(),
                    buf.readUUID(), readString(buf, MAX_CLIP_ID_CHARS), buf.readLong(),
                    buf.readEnum(Source.class), buf.readBoolean())));

        public Playback {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(source, "source");
            clipId = boundedString(clipId, MAX_CLIP_ID_CHARS, "clipId");
            if (clipId.isEmpty() || startTick < 0) {
                throw new IllegalArgumentException("Invalid playback state");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Bounded client control request; PLAY/STOP are forwarded to the installed server handler. */
    public record Control(Action action, UUID ticket, UUID target, String clipId, Trigger trigger,
                          int slot) implements CustomPacketPayload {
        public static final Type<Control> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "dance_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Control> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action());
                buf.writeUUID(packet.ticket());
                buf.writeUUID(packet.target());
                writeString(buf, packet.clipId(), MAX_CLIP_ID_CHARS);
                buf.writeBoolean(packet.trigger() != null);
                if (packet.trigger() != null) {
                    buf.writeEnum(packet.trigger());
                }
                buf.writeByte(packet.slot());
            },
            buf -> decode(() -> {
                Action action = buf.readEnum(Action.class);
                UUID ticket = buf.readUUID();
                UUID target = buf.readUUID();
                String clipId = readString(buf, MAX_CLIP_ID_CHARS);
                Trigger trigger = buf.readBoolean() ? buf.readEnum(Trigger.class) : null;
                int slot = buf.readByte();
                if (slot < -1 || slot >= Prefs.SLOT_COUNT) {
                    throw new DecoderException("Dance control slot out of bounds: " + slot);
                }
                return new Control(action, ticket, target, clipId, trigger, slot);
            }));

        public Control {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            Objects.requireNonNull(target, "target");
            if (!isControl(action)) {
                throw new IllegalArgumentException("Not a control action: " + action);
            }
            clipId = boundedString(clipId, MAX_CLIP_ID_CHARS, "clipId");
            if (action == Action.SET_PREF && trigger == null) {
                throw new IllegalArgumentException("SET_PREF requires a trigger");
            }
            if (action == Action.SET_SLOT && (slot < 0 || slot >= Prefs.SLOT_COUNT)) {
                throw new IllegalArgumentException("SET_SLOT requires a valid slot");
            }
            if ((action == Action.PLAY) && clipId.isEmpty()) {
                throw new IllegalArgumentException("PLAY requires a clip id");
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writePrefs(RegistryFriendlyByteBuf buf, Prefs prefs) {
        writeString(buf, prefs.music(), MAX_CLIP_ID_CHARS);
        writeString(buf, prefs.revival(), MAX_CLIP_ID_CHARS);
        writeString(buf, prefs.maidMusic(), MAX_CLIP_ID_CHARS);
        writeString(buf, prefs.animalMusic(), MAX_CLIP_ID_CHARS);
        buf.writeVarInt(prefs.slots().size());
        for (String slot : prefs.slots()) {
            writeString(buf, slot, MAX_CLIP_ID_CHARS);
        }
    }

    private static Prefs readPrefs(RegistryFriendlyByteBuf buf) {
        String music = readString(buf, MAX_CLIP_ID_CHARS);
        String revival = readString(buf, MAX_CLIP_ID_CHARS);
        String maidMusic = readString(buf, MAX_CLIP_ID_CHARS);
        String animalMusic = readString(buf, MAX_CLIP_ID_CHARS);
        int count = buf.readVarInt();
        if (count != Prefs.SLOT_COUNT) {
            throw new DecoderException("Dance preference slot count out of bounds: " + count);
        }
        List<String> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(readString(buf, MAX_CLIP_ID_CHARS));
        }
        return new Prefs(music, revival, maidMusic, animalMusic, slots);
    }

    private static void writeBuiltin(RegistryFriendlyByteBuf buf, DanceLibrary.Builtin builtin) {
        writeString(buf, builtin.id(), MAX_CLIP_ID_CHARS);
        writeString(buf, builtin.translationKey(), MAX_MESSAGE_CHARS);
        buf.writeFloat(builtin.durationSeconds());
    }

    private static DanceLibrary.Builtin readBuiltin(RegistryFriendlyByteBuf buf) {
        return new DanceLibrary.Builtin(readString(buf, MAX_CLIP_ID_CHARS),
            readString(buf, MAX_MESSAGE_CHARS), buf.readFloat());
    }

    private static void writeClip(RegistryFriendlyByteBuf buf, DanceLibrary.Clip clip) {
        writeString(buf, clip.id(), MAX_CLIP_ID_CHARS);
        writeString(buf, clip.hash(), MAX_CLIP_ID_CHARS);
        buf.writeUUID(clip.owner());
        writeString(buf, clip.title(), MAX_TITLE_CHARS);
        buf.writeVarInt(clip.bytes());
        buf.writeFloat(clip.duration());
        buf.writeVarInt(clip.frames());
    }

    private static DanceLibrary.Clip readClip(RegistryFriendlyByteBuf buf) {
        return new DanceLibrary.Clip(readString(buf, MAX_CLIP_ID_CHARS),
            readString(buf, MAX_CLIP_ID_CHARS), buf.readUUID(),
            readString(buf, MAX_TITLE_CHARS), buf.readVarInt(), buf.readFloat(), buf.readVarInt());
    }

    private static void writeString(RegistryFriendlyByteBuf buf, String value, int maxChars) {
        buf.writeUtf(boundedString(value, maxChars, "string"), maxChars * 3);
    }

    private static String readString(RegistryFriendlyByteBuf buf, int maxChars) {
        return boundedString(buf.readUtf(maxChars), maxChars, "string");
    }

    private static String boundedString(String value, int maxChars, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > maxChars) {
            throw new DecoderException(field + " exceeds " + maxChars + " characters");
        }
        return value;
    }

    private static <T> T decode(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (DecoderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DecoderException("Invalid dance packet", e);
        }
    }
}
