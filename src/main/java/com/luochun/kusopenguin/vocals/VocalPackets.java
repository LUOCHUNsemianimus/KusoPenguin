package com.luochun.kusopenguin.vocals;

import com.luochun.kusopenguin.KusoPenguin;
import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/**
 * Bounded wire contract for the vocal editing screen.
 *
 * <p>No handler or registration lives here. Every string is length-checked before allocation on
 * decode and on encode; hashes are empty or exactly 64 lowercase hex characters. The records reject
 * malformed values at construction so a decoded packet cannot carry out-of-range numbers.
 */
public final class VocalPackets {
    public static final int MAX_RESOURCE_ID_CHARS = 256;
    public static final int MAX_HASH_CHARS = 64;
    public static final int MAX_STATUS_CHARS = 128;

    private VocalPackets() {
    }

    public enum Action {
        OPEN, BIND, REMOVE, TOGGLE, PLAY, PAUSE
    }

    /** One edit request. OPEN ignores the edit fields; the server re-reads all state itself. */
    public record Request(Action action, UUID ticket, InteractionHand hand, String trackId,
                          int offsetMs, float gain, boolean enabled)
        implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "vocal_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
            StreamCodec.of(
                (buf, packet) -> {
                    buf.writeEnum(packet.action());
                    buf.writeUUID(packet.ticket());
                    buf.writeEnum(packet.hand());
                    writeString(buf, packet.trackId(), MAX_HASH_CHARS);
                    buf.writeInt(packet.offsetMs());
                    buf.writeFloat(packet.gain());
                    buf.writeBoolean(packet.enabled());
                },
                buf -> new Request(buf.readEnum(Action.class), buf.readUUID(),
                    buf.readEnum(InteractionHand.class), readString(buf, MAX_HASH_CHARS),
                    buf.readInt(), buf.readFloat(), buf.readBoolean()));

        public Request {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(ticket, "ticket");
            Objects.requireNonNull(hand, "hand");
            trackId = boundedString(trackId, MAX_HASH_CHARS, "trackId");
            requireEmptyOrHash(trackId, "trackId");
            requireRange(offsetMs, gain);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server reply and current item snapshot. {@code binding} is the selected melody's persisted
     * binding, or null. The server always re-reads the live held stack before reporting it.
     */
    public record State(boolean open, UUID ticket, InteractionHand hand, String itemId,
                        String melodyId, String melodyHash, VocalData.Binding binding,
                        boolean enabled, String status) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(KusoPenguin.MODID, "vocal_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
            StreamCodec.of(
                (buf, packet) -> {
                    buf.writeBoolean(packet.open());
                    buf.writeUUID(packet.ticket());
                    buf.writeEnum(packet.hand());
                    writeString(buf, packet.itemId(), MAX_RESOURCE_ID_CHARS);
                    writeString(buf, packet.melodyId(), MAX_RESOURCE_ID_CHARS);
                    writeString(buf, packet.melodyHash(), MAX_HASH_CHARS);
                    buf.writeBoolean(packet.binding() != null);
                    if (packet.binding() != null) {
                        writeBinding(buf, packet.binding());
                    }
                    buf.writeBoolean(packet.enabled());
                    writeString(buf, packet.status(), MAX_STATUS_CHARS);
                },
                buf -> {
                    boolean open = buf.readBoolean();
                    UUID ticket = buf.readUUID();
                    InteractionHand hand = buf.readEnum(InteractionHand.class);
                    String itemId = readString(buf, MAX_RESOURCE_ID_CHARS);
                    String melodyId = readString(buf, MAX_RESOURCE_ID_CHARS);
                    String melodyHash = readString(buf, MAX_HASH_CHARS);
                    VocalData.Binding binding = buf.readBoolean() ? readBinding(buf) : null;
                    boolean enabled = buf.readBoolean();
                    String status = readString(buf, MAX_STATUS_CHARS);
                    return new State(open, ticket, hand, itemId, melodyId, melodyHash, binding,
                        enabled, status);
                });

        public State {
            Objects.requireNonNull(ticket, "ticket");
            Objects.requireNonNull(hand, "hand");
            itemId = boundedString(itemId, MAX_RESOURCE_ID_CHARS, "itemId");
            melodyId = boundedString(melodyId, MAX_RESOURCE_ID_CHARS, "melodyId");
            melodyHash = boundedString(melodyHash, MAX_HASH_CHARS, "melodyHash");
            requireEmptyOrHash(melodyHash, "melodyHash");
            status = boundedString(status, MAX_STATUS_CHARS, "status");
            if (binding != null) {
                binding = boundedBinding(binding);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeBinding(RegistryFriendlyByteBuf buf, VocalData.Binding binding) {
        writeString(buf, binding.melodyId(), MAX_RESOURCE_ID_CHARS);
        writeString(buf, binding.melodyHash(), MAX_HASH_CHARS);
        writeString(buf, binding.trackId(), MAX_HASH_CHARS);
        buf.writeInt(binding.offsetMs());
        buf.writeFloat(binding.gain());
    }

    private static VocalData.Binding readBinding(RegistryFriendlyByteBuf buf) {
        return new VocalData.Binding(readString(buf, MAX_RESOURCE_ID_CHARS),
            readString(buf, MAX_HASH_CHARS), readString(buf, MAX_HASH_CHARS),
            buf.readInt(), buf.readFloat());
    }

    private static VocalData.Binding boundedBinding(VocalData.Binding binding) {
        String id = boundedString(binding.melodyId(), MAX_RESOURCE_ID_CHARS, "binding.melodyId");
        String melodyHash = boundedString(binding.melodyHash(), MAX_HASH_CHARS,
            "binding.melodyHash");
        String trackId = boundedString(binding.trackId(), MAX_HASH_CHARS, "binding.trackId");
        requireEmptyOrHash(melodyHash, "binding.melodyHash");
        requireEmptyOrHash(trackId, "binding.trackId");
        requireRange(binding.offsetMs(), binding.gain());
        return new VocalData.Binding(id, melodyHash, trackId, binding.offsetMs(), binding.gain());
    }

    private static void requireRange(int offsetMs, float gain) {
        if (offsetMs < VocalData.MIN_OFFSET_MS || offsetMs > VocalData.MAX_OFFSET_MS) {
            throw new DecoderException("offsetMs out of bounds: " + offsetMs);
        }
        if (!Float.isFinite(gain) || gain < 0.0F || gain > VocalData.MAX_GAIN) {
            throw new DecoderException("gain out of bounds: " + gain);
        }
    }

    private static void requireEmptyOrHash(String value, String field) {
        if (!value.isEmpty() && !isLowerHex64(value)) {
            throw new DecoderException(field + " must be empty or 64 lowercase hex characters");
        }
    }

    private static boolean isLowerHex64(String value) {
        if (value.length() != 64) {
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
}
