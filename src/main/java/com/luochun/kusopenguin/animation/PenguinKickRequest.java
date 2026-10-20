package com.luochun.kusopenguin.animation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** No client-selected victim, position, direction or damage. */
public record PenguinKickRequest() implements CustomPacketPayload {
    public static final PenguinKickRequest INSTANCE=new PenguinKickRequest();
    public static final Type<PenguinKickRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("kuso_penguin","kick_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PenguinKickRequest> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
