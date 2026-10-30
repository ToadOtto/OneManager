package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockpickEndS2CPacket(boolean success, String message) implements CustomPacketPayload {
    public static final Type<LockpickEndS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "lockpick_end"));

    public static final StreamCodec<ByteBuf, LockpickEndS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LockpickEndS2CPacket::success,
            ByteBufCodecs.STRING_UTF8, LockpickEndS2CPacket::message,
            LockpickEndS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickEndS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.closeLockpickScreen(payload.success(), payload.message());
        });
    }
}
