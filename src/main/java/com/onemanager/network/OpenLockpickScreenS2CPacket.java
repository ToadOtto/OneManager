package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenLockpickScreenS2CPacket(int totalTicks, String toolName) implements CustomPacketPayload {
    public static final Type<OpenLockpickScreenS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "open_lockpick_screen"));

    public static final StreamCodec<ByteBuf, OpenLockpickScreenS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenLockpickScreenS2CPacket::totalTicks,
            ByteBufCodecs.STRING_UTF8, OpenLockpickScreenS2CPacket::toolName,
            OpenLockpickScreenS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLockpickScreenS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.openLockpickScreen(payload.totalTicks(), payload.toolName());
        });
    }
}
