package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCharHistoryPacket(CompoundTag playerData) implements CustomPacketPayload {
    public static final Type<OpenCharHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "open_char_history"));

    public static final StreamCodec<ByteBuf, OpenCharHistoryPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, OpenCharHistoryPacket::playerData,
            OpenCharHistoryPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCharHistoryPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.openCharHistoryScreen(payload.playerData());
        });
    }
}
