package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenTreasuryReasonScreenS2CPacket(int amount, boolean demolition, String coords) implements CustomPacketPayload {
    public static final Type<OpenTreasuryReasonScreenS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "open_treasury_reason_screen"));

    public static final StreamCodec<ByteBuf, OpenTreasuryReasonScreenS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenTreasuryReasonScreenS2CPacket::amount,
            ByteBufCodecs.BOOL, OpenTreasuryReasonScreenS2CPacket::demolition,
            ByteBufCodecs.STRING_UTF8, OpenTreasuryReasonScreenS2CPacket::coords,
            OpenTreasuryReasonScreenS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTreasuryReasonScreenS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.openTreasuryReasonScreen(payload.amount(), payload.demolition(), payload.coords());
        });
    }
}
