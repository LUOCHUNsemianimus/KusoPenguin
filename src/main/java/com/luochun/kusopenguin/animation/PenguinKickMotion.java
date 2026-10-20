package com.luochun.kusopenguin.animation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record PenguinKickMotion(ResourceLocation dimension,double x,double y,double z,double dx,double dz) implements CustomPacketPayload {
    public static final Type<PenguinKickMotion> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin","kick_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PenguinKickMotion> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeResourceLocation(p.dimension);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.dx);b.writeDouble(p.dz);},
        b->new PenguinKickMotion(b.readResourceLocation(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
