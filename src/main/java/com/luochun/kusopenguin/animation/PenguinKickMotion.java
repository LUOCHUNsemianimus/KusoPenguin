package com.luochun.kusopenguin.animation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/**
 * Server-approved kick motion. {@code sequence} identifies the exact PoseSync kick; {@code playing}
 * false is a stop for that same local mirror and carries zeroed trajectory fields.
 */
public record PenguinKickMotion(ResourceLocation dimension,long sequence,boolean playing,
                                double x,double y,double z,double dx,double dz,double distance) implements CustomPacketPayload {
    public static final Type<PenguinKickMotion> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin","kick_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PenguinKickMotion> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeResourceLocation(p.dimension);b.writeVarLong(p.sequence);b.writeBoolean(p.playing);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.dx);b.writeDouble(p.dz);b.writeDouble(p.distance);},
        b->new PenguinKickMotion(b.readResourceLocation(),b.readVarLong(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
