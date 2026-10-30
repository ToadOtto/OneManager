package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockpickQteS2CPacket(int qteId, int targetDigit, int timeoutTicks) implements CustomPacketPayload {
    public static final Type<LockpickQteS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "lockpick_qte"));

    public static final StreamCodec<ByteBuf, LockpickQteS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockpickQteS2CPacket::qteId,
            ByteBufCodecs.VAR_INT, LockpickQteS2CPacket::targetDigit,
            ByteBufCodecs.VAR_INT, LockpickQteS2CPacket::timeoutTicks,
            LockpickQteS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickQteS2CPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandler.triggerLockpickQte(payload.qteId(), payload.targetDigit(), payload.timeoutTicks());
        });
    }
}
