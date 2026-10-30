package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.heist.HeistManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockpickCancelC2SPacket() implements CustomPacketPayload {
    public static final Type<LockpickCancelC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "lockpick_cancel"));

    public static final StreamCodec<ByteBuf, LockpickCancelC2SPacket> STREAM_CODEC = StreamCodec.unit(new LockpickCancelC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickCancelC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HeistManager.onCancel(player);
            }
        });
    }
}
