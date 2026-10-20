package com.luochun.kusopenguin.animation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Small server-owned cosmetic event. Effect duration is never used as a timestamp. */
public record PenguinPosePayload(ResourceLocation dimension,int entityId,UUID uuid,
                                 long sequence,long startedGameTime,boolean playing,
                                 PenguinAction action) implements CustomPacketPayload {
    public static final Type<PenguinPosePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin","pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PenguinPosePayload> STREAM_CODEC=StreamCodec.of(
        (buf,p)->{buf.writeResourceLocation(p.dimension());buf.writeVarInt(p.entityId());buf.writeUUID(p.uuid());buf.writeVarLong(p.sequence());buf.writeLong(p.startedGameTime());buf.writeBoolean(p.playing());buf.writeEnum(p.action());},
        buf->new PenguinPosePayload(buf.readResourceLocation(),buf.readVarInt(),buf.readUUID(),buf.readVarLong(),buf.readLong(),buf.readBoolean(),buf.readEnum(PenguinAction.class)));

    /** Compatibility constructor for existing Taunt call tools/tests. */
    public PenguinPosePayload(ResourceLocation dimension,int entityId,UUID uuid,
                              long sequence,long startedGameTime,boolean playing) {
        this(dimension,entityId,uuid,sequence,startedGameTime,playing,PenguinAction.TAUNT);
    }

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
