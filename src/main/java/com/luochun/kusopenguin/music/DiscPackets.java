package com.luochun.kusopenguin.music;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded wire records for the disc/music feature. This step intentionally contains no
 * handlers or registration; the records only define the later transport contract.
 *
 * <p>All strings are bounded on both encode and decode. Byte arrays are bounded before
 * allocation and cloned on construction/access. Numeric fields that describe an upload are
 * checked by the server later; the codec must not be mistaken for authorization.</p>
 */
public final class DiscPackets {
    public static final int MAX_TRACK_ID_CHARS = 64;
    public static final int MAX_TITLE_CHARS = 128;
    public static final int MAX_MESSAGE_CHARS = 256;
    public static final int MAX_CATALOG_TRACKS = 100;

    private DiscPackets() {
    }

    public enum Action {
        LIST, BEGIN_UPLOAD, END_UPLOAD, CANCEL_UPLOAD, SELECT, DOWNLOAD
    }

    /** Client request or upload control. {@code totalBytes} is advisory until server validation. */
    public record Request(Action action, UUID ticket, String trackId, String title, int totalBytes)
        implements CustomPacketPayload {
        public static final Type<Request> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action());
                buf.writeUUID(packet.ticket());
                writeString(buf, packet.trackId(), MAX_TRACK_ID_CHARS);
                writeString(buf, packet.title(), MAX_TITLE_CHARS);
                buf.writeInt(packet.totalBytes());
            },
            buf -> new Request(buf.readEnum(Action.class), buf.readUUID(),
                readString(buf, MAX_TRACK_ID_CHARS), readString(buf, MAX_TITLE_CHARS), buf.readInt()));

        public Request {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            trackId = boundedString(trackId, MAX_TRACK_ID_CHARS, "trackId");
            title = boundedString(title, MAX_TITLE_CHARS, "title");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One bounded WAV chunk for upload/download; {@code data} is never exposed mutably. */
    public record Chunk(UUID ticket, int offset, byte[] data) implements CustomPacketPayload {
        public static final int MAX_CHUNK = WaveFile.MAX_CHUNK;
        public static final Type<Chunk> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.ticket());
                buf.writeVarInt(packet.offset());
                buf.writeByteArray(packet.data());
            },
            buf -> new Chunk(buf.readUUID(), buf.readVarInt(),
                // readByteArray(max) checks the declared length before allocating.
                buf.readByteArray(MAX_CHUNK)));

        public Chunk {
            Objects.requireNonNull(ticket, "ticket");
            Objects.requireNonNull(data, "data");
            if (data.length > MAX_CHUNK) {
                throw new IllegalArgumentException("Chunk data exceeds " + MAX_CHUNK + " bytes");
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

    /** Server acknowledgement for an action or upload step. */
    public record Reply(Action action, UUID ticket, boolean success, String message, String trackId,
                        int totalBytes) implements CustomPacketPayload {
        public static final Type<Reply> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeEnum(packet.action());
                buf.writeUUID(packet.ticket());
                buf.writeBoolean(packet.success());
                writeString(buf, packet.message(), MAX_MESSAGE_CHARS);
                writeString(buf, packet.trackId(), MAX_TRACK_ID_CHARS);
                buf.writeInt(packet.totalBytes());
            },
            buf -> new Reply(buf.readEnum(Action.class), buf.readUUID(), buf.readBoolean(),
                readString(buf, MAX_MESSAGE_CHARS), readString(buf, MAX_TRACK_ID_CHARS), buf.readInt()));

        public Reply {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            message = boundedString(message, MAX_MESSAGE_CHARS, "message");
            trackId = boundedString(trackId, MAX_TRACK_ID_CHARS, "trackId");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Immutable, bounded music metadata snapshot. */
    public record Catalog(boolean open, List<MusicLibrary.Track> tracks) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.open());
                buf.writeVarInt(packet.tracks().size());
                for (MusicLibrary.Track track : packet.tracks()) {
                    writeTrack(buf, track);
                }
            },
            buf -> {
                boolean open = buf.readBoolean();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_CATALOG_TRACKS) {
                    // Validate the declared collection size before allocating the list.
                    throw new DecoderException("Catalog track count out of bounds: " + count);
                }
                List<MusicLibrary.Track> tracks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    tracks.add(readTrack(buf));
                }
                return new Catalog(open, tracks);
            });

        public Catalog {
            Objects.requireNonNull(tracks, "tracks");
            if (tracks.size() > MAX_CATALOG_TRACKS) {
                throw new IllegalArgumentException("Catalog exceeds " + MAX_CATALOG_TRACKS + " tracks");
            }
            tracks = List.copyOf(tracks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Current playback state broadcast for a selected track at a block position. */
    public record Playback(ResourceLocation dimension, BlockPos pos, UUID session, String trackId,
                           long startedGameTime, int durationTicks, boolean playing)
        implements CustomPacketPayload {
        public static final Type<Playback> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_playback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Playback> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeResourceLocation(packet.dimension());
                buf.writeBlockPos(packet.pos());
                buf.writeUUID(packet.session());
                writeString(buf, packet.trackId(), MAX_TRACK_ID_CHARS);
                buf.writeLong(packet.startedGameTime());
                buf.writeVarInt(packet.durationTicks());
                buf.writeBoolean(packet.playing());
            },
            buf -> new Playback(buf.readResourceLocation(), buf.readBlockPos(), buf.readUUID(),
                readString(buf, MAX_TRACK_ID_CHARS), buf.readLong(), buf.readVarInt(), buf.readBoolean()));

        public Playback {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(session, "session");
            trackId = boundedString(trackId, MAX_TRACK_ID_CHARS, "trackId");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Dance playback state for one entity. */
    public record Dance(ResourceLocation dimension, UUID entity, UUID session, long startedGameTime,
                        boolean playing) implements CustomPacketPayload {
        public static final Type<Dance> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "disc_dance"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Dance> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeResourceLocation(packet.dimension());
                buf.writeUUID(packet.entity());
                buf.writeUUID(packet.session());
                buf.writeLong(packet.startedGameTime());
                buf.writeBoolean(packet.playing());
            },
            buf -> new Dance(buf.readResourceLocation(), buf.readUUID(), buf.readUUID(),
                buf.readLong(), buf.readBoolean()));

        public Dance {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(session, "session");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeTrack(RegistryFriendlyByteBuf buf, MusicLibrary.Track track) {
        Objects.requireNonNull(track, "track");
        writeString(buf, track.id(), MAX_TRACK_ID_CHARS);
        writeString(buf, track.title(), MAX_TITLE_CHARS);
        buf.writeUUID(track.owner());
        buf.writeVarInt(track.sampleRate());
        buf.writeVarInt(track.dataOffset());
        buf.writeVarInt(track.pcmBytes());
        buf.writeVarInt(track.totalBytes());
        buf.writeVarInt(track.durationTicks());
    }

    private static MusicLibrary.Track readTrack(RegistryFriendlyByteBuf buf) {
        String id = readString(buf, MAX_TRACK_ID_CHARS);
        String title = readString(buf, MAX_TITLE_CHARS);
        UUID owner = buf.readUUID();
        int sampleRate = buf.readVarInt();
        int dataOffset = buf.readVarInt();
        int pcmBytes = buf.readVarInt();
        int totalBytes = buf.readVarInt();
        int durationTicks = buf.readVarInt();
        return new MusicLibrary.Track(id, title, owner, sampleRate, dataOffset, pcmBytes, totalBytes,
            durationTicks);
    }

    private static void writeString(RegistryFriendlyByteBuf buf, String value, int maxChars) {
        // writeUtf's limit is encoded bytes; allow up to 3 bytes per UTF-16 code unit and let the
        // decoder enforce the character count.
        buf.writeUtf(boundedString(value, maxChars, "string"), maxChars * 3);
    }

    private static String readString(RegistryFriendlyByteBuf buf, int maxChars) {
        // readUtf checks the declared byte length before allocation and the decoded char count.
        return boundedString(buf.readUtf(maxChars), maxChars, "string");
    }

    private static String boundedString(String value, int maxChars, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > maxChars) {
            throw new DecoderException(field + " exceeds " + maxChars + " characters");
        }
        return value;
    }
}
