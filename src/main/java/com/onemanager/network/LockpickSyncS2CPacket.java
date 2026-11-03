package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockpickSyncS2CPacket(int elapsedTicks, int totalTicks) implements CustomPacketPayload {
    public static final Type<LockpickSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "lockpick_sync"));

    public static final StreamCodec<ByteBuf, LockpickSyncS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockpickSyncS2CPacket::elapsedTicks,
            ByteBufCodecs.VAR_INT, LockpickSyncS2CPacket::totalTicks,
            LockpickSyncS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickSyncS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.syncLockpick(payload.elapsedTicks(), payload.totalTicks());
        });
    }
}
