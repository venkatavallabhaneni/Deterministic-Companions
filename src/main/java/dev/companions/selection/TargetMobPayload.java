package dev.companions.selection;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TargetMobPayload(int entityId) implements CustomPacketPayload {
    public static final Type<TargetMobPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("deterministic_companions","target_mob"));
    public static final StreamCodec<FriendlyByteBuf,TargetMobPayload> CODEC=StreamCodec.of(
        (buffer,payload)->buffer.writeVarInt(payload.entityId()),buffer->new TargetMobPayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
