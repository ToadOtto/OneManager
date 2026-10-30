package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.heist.HeistManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockpickQteAnswerC2SPacket(int qteId, int pressedDigit) implements CustomPacketPayload {
    public static final Type<LockpickQteAnswerC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "lockpick_qte_answer"));

    public static final StreamCodec<ByteBuf, LockpickQteAnswerC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockpickQteAnswerC2SPacket::qteId,
            ByteBufCodecs.VAR_INT, LockpickQteAnswerC2SPacket::pressedDigit,
            LockpickQteAnswerC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickQteAnswerC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HeistManager.onQteAnswer(player, payload.qteId(), payload.pressedDigit());
            }
        });
    }
}
