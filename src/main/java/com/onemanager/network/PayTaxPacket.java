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

public record PayTaxPacket(long amount) implements CustomPacketPayload {
    public static final Type<PayTaxPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "pay_tax"));

    public static final StreamCodec<ByteBuf, PayTaxPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PayTaxPacket::amount,
            PayTaxPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PayTaxPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            double balance = EconomyManager.getBalance(player);
            int taxRate = worldData.getTaxRate();
            long maxStatus = taxRate * 3L;

            PlayerCharacterData pd = worldData.getOrCreatePlayerData(player.getUUID());
            long currentStatus = pd.getTaxStatus();
            long needed = Math.max(0, maxStatus - currentStatus);

            long toPay = Math.min(payload.amount(), needed);
            if (toPay <= 0 || balance < toPay) {
                if (balance < toPay) {
                    player.sendSystemMessage(Component.literal("❌ Недостаточно средств на балансе!").withStyle(ChatFormatting.RED));
                } else {
                    player.sendSystemMessage(Component.literal("✔ Ваш налоговый статус уже максимален!").withStyle(ChatFormatting.GREEN));
                }
                return;
            }

            if (!EconomyManager.deductBalance(player, toPay)) {
                player.sendSystemMessage(Component.literal("❌ Недостаточно средств на балансе!").withStyle(ChatFormatting.RED));
                return;
            }
            pd.increaseTaxStatus(toPay, maxStatus);
            worldData.addTreasury(toPay);

            String charName = pd.hasActiveCharacter() ? pd.getActiveCharacter().getName() : player.getScoreboardName();
            String pName = player.getScoreboardName();
            String actionDesc = "+" + String.format("%,d", toPay).replace(',', ' ') + " $PR (Оплата налога)";

            worldData.logTransaction(charName, pName, actionDesc, worldData.getTreasury());
            worldData.setDirty();

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            String statusStr = (pd.getTaxStatus() > 0 ? "+" : "") + pd.getTaxStatus() + " $PR";
            ChatFormatting col = pd.getTaxStatus() < 0 ? ChatFormatting.RED : (pd.getTaxStatus() >= taxRate ? ChatFormatting.GREEN : ChatFormatting.WHITE);

            player.sendSystemMessage(
                    Component.literal("✔ Налог оплачен: ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(String.format("%,d", toPay).replace(',', ' ') + " $PR").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" (Налоговый статус: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(statusStr).withStyle(col, ChatFormatting.BOLD))
                            .append(Component.literal(")").withStyle(ChatFormatting.GRAY))
            );
        });
    }
}
