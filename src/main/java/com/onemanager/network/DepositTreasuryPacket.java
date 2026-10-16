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

public record DepositTreasuryPacket(long amount) implements CustomPacketPayload {
    public static final Type<DepositTreasuryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "deposit_treasury"));

    public static final StreamCodec<ByteBuf, DepositTreasuryPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DepositTreasuryPacket::amount,
            DepositTreasuryPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DepositTreasuryPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            long freeSpace = worldData.getTreasuryFreeSpace();
            if (freeSpace <= 0) {
                player.sendSystemMessage(Component.literal("❌ В хранилищах казны нет свободного места! Установите новые сундуки казны.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            double balance = EconomyManager.getBalance(player);
            long toPay = Math.min(payload.amount(), freeSpace);

            if (toPay > 0 && balance >= toPay) {
                if (!EconomyManager.deductBalance(player, toPay)) {
                    player.sendSystemMessage(Component.literal("❌ Недостаточно средств на балансе!").withStyle(ChatFormatting.RED));
                    return;
                }
                PlayerCharacterData pd = worldData.getOrCreatePlayerData(player.getUUID());
                worldData.depositToChests(player.server, toPay);

                int marksEarned = pd.applyBrandDeposit(toPay);
                if (marksEarned > 0) {
                    pd.addStateBrands(marksEarned);
                }

                String charName = pd.hasActiveCharacter() ? pd.getActiveCharacter().getName() : player.getScoreboardName();
                String pName = player.getScoreboardName();
                String actionDesc = "+" + String.format("%,d", toPay).replace(',', ' ') + " $PR (Взнос в казну" + (marksEarned > 0 ? ", +" + marksEarned + " марок на счёт" : "") + ")";

                worldData.logTransaction(charName, pName, actionDesc, worldData.getTreasury());
                worldData.setDirty();

                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                Component msg = Component.literal("💰 Вы внесли в казну: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(String.format("%,d", toPay).replace(',', ' ') + " $PR")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                if (marksEarned > 0) {
                    msg = msg.copy().append(Component.literal(" [Начислено Гос. Марок на счёт: +" + marksEarned + "]")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                player.sendSystemMessage(msg);
            } else if (toPay > balance) {
                player.sendSystemMessage(
                        Component.literal("❌ Недостаточно средств на балансе!").withStyle(ChatFormatting.RED)
                );
            }
        });
    }
}
