package com.luochun.kusopenguin.revival;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Small, server-authoritative revival protocol. A player request carries the exact corpse marker
 * UUID (the death-generation token), or the exact tombstone UUID for a maid target.
 */
public final class RevivalPackets {
    private RevivalPackets() {
    }

    public record Request(UUID target) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "revival_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.of((buf, value) -> buf.writeUUID(value.target()),
                        buf -> new Request(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record State(ResourceLocation dimension, int entityId, UUID caster, UUID target,
                        boolean maidTarget, long startedGameTime, int session, boolean active)
            implements CustomPacketPayload {
        public static final Type<State> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin", "revival_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeResourceLocation(value.dimension());
                    buf.writeVarInt(value.entityId());
                    buf.writeUUID(value.caster());
                    buf.writeUUID(value.target());
                    buf.writeBoolean(value.maidTarget());
                    buf.writeVarLong(value.startedGameTime());
                    buf.writeVarInt(value.session());
                    buf.writeBoolean(value.active());
                }, buf -> new State(buf.readResourceLocation(), buf.readVarInt(), buf.readUUID(),
                        buf.readUUID(), buf.readBoolean(), buf.readVarLong(), buf.readVarInt(),
                        buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
