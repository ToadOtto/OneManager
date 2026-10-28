package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.data.TreasuryChestEntry;
import com.onemanager.economy.EconomyManager;
import com.onemanager.item.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuyTreasuryChestC2SPacket(boolean large) implements CustomPacketPayload {
    public static final Type<BuyTreasuryChestC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "buy_treasury_chest"));

    public static final StreamCodec<ByteBuf, BuyTreasuryChestC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BuyTreasuryChestC2SPacket::large,
            BuyTreasuryChestC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuyTreasuryChestC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (!worldData.canAccessTreasury(player.getUUID())) {
                player.sendSystemMessage(Component.literal("❌ Покупать хранилища казны может только Князь!")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            int cost = payload.large() ? TreasuryChestEntry.LARGE_COST : TreasuryChestEntry.SMALL_COST;
            String chestTitle = payload.large() ? "Большой Сундук Казны" : "Малый Сундук Казны";
            long currentTreasury = worldData.getTreasury();

            if (currentTreasury >= cost) {
                worldData.withdrawFromChests(player.server, cost);
            } else {
                // Bootstrap fallback if no chests exist or treasury is below cost:
                // check if treasury + player's personal balance covers the cost
                long shortfall = cost - currentTreasury;
                double personalBal = EconomyManager.getBalance(player);
                if (personalBal >= shortfall) {
                    if (!EconomyManager.deductBalance(player, shortfall)) {
                        player.sendSystemMessage(Component.literal("❌ В казне недостаточно средств (" + currentTreasury + " / " + cost + " $PR)!")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (currentTreasury > 0) {
                        worldData.withdrawFromChests(player.server, currentTreasury);
                    }
                } else {
                    player.sendSystemMessage(Component.literal("❌ В казне недостаточно средств для покупки (" + currentTreasury + " / " + cost + " $PR)!")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
            }

            ItemStack chestStack = new ItemStack(payload.large() ? ModItems.LARGE_TREASURY_CHEST.get() : ModItems.SMALL_TREASURY_CHEST.get());
            if (!player.getInventory().add(chestStack)) {
                player.drop(chestStack, false);
            }
            player.inventoryMenu.broadcastChanges();

            PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
            String charName = (pd != null && pd.hasActiveCharacter()) ? pd.getActiveCharacter().getName() : player.getScoreboardName();
            String pName = player.getScoreboardName();

            String actionDesc = "-" + String.format("%,d", cost).replace(',', ' ') + " $PR (Покупка хранилища: " + chestTitle + ")";
            worldData.logTransaction(charName, pName, actionDesc, worldData.getTreasury());
            worldData.setDirty();

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            player.sendSystemMessage(
                    Component.literal("🏦 Вы приобрели ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(chestTitle).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" за " + cost + " $PR! Установите его в мире для расширения вместимости казны.")
                                    .withStyle(ChatFormatting.GRAY))
            );
        });
    }
}
