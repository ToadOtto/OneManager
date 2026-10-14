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
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            boolean can = worldData.canManageTaxes(player.getUUID()) || player.hasPermissions(2);
            if (!can) {
                player.sendSystemMessage(Component.literal("❌ Только Князь или Казначей могут снимать средства из казны!").withStyle(ChatFormatting.RED));
                return;
            }

            long toWithdraw = payload.amount();
            long treasury = worldData.getTreasury();

            if (toWithdraw > 0 && treasury >= toWithdraw) {
                worldData.removeTreasury(toWithdraw);
                EconomyManager.addBalance(worldData, player.getUUID(), toWithdraw);

                PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
                String charName = pd != null && pd.hasActiveCharacter() ? pd.getActiveCharacter().getName() : player.getScoreboardName();
                String pName = player.getScoreboardName();

                String actionDesc = "-" + String.format("%,d", toWithdraw).replace(',', ' ') + " $PR (Снятие из казны)";
                worldData.logTransaction(charName, pName, actionDesc, worldData.getTreasury());
                worldData.setDirty();

                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                player.sendSystemMessage(
                        Component.literal("💸 Вы сняли из казны: ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(String.format("%,d", toWithdraw).replace(',', ' ') + " $PR")
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                .append(Component.literal(" (Остаток в казне: " + String.format("%,d", worldData.getTreasury()).replace(',', ' ') + " $PR)")
                                        .withStyle(ChatFormatting.GRAY))
                );
            } else if (toWithdraw > treasury) {
                player.sendSystemMessage(
                        Component.literal("❌ В казне недостаточно средств!").withStyle(ChatFormatting.RED)
                );
            }
        });
    }
}
