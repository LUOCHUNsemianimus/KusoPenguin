package com.luochun.kusopenguin.animation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Client report that its local kick motion was interrupted. Carries only the kick sequence. */
public record PenguinKickCancel(long sequence) implements CustomPacketPayload {
    public static final Type<PenguinKickCancel> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin","kick_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PenguinKickCancel> STREAM_CODEC=StreamCodec.of(
        (b,p)->b.writeVarLong(p.sequence()),
        b->new PenguinKickCancel(b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
