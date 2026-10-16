package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.economy.EconomyManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WithdrawTreasuryPacket(long amount) implements CustomPacketPayload {
    public static final Type<WithdrawTreasuryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "withdraw_treasury"));

    public static final StreamCodec<ByteBuf, WithdrawTreasuryPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, WithdrawTreasuryPacket::amount,
            WithdrawTreasuryPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WithdrawTreasuryPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            player.sendSystemMessage(
                    Component.literal("❌ Забрать деньги из казны можно только напрямую из Сундуков Казны в мире!")
                            .withStyle(ChatFormatting.RED)
            );
        });
    }
}
