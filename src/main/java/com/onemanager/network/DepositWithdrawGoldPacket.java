package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DepositWithdrawGoldPacket(boolean isDeposit, int amount) implements CustomPacketPayload {
    public static final Type<DepositWithdrawGoldPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "gold_transfer_c2s"));

    public static final StreamCodec<ByteBuf, DepositWithdrawGoldPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DepositWithdrawGoldPacket::isDeposit,
            ByteBufCodecs.VAR_INT, DepositWithdrawGoldPacket::amount,
            DepositWithdrawGoldPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DepositWithdrawGoldPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) {
                return;
            }
            int amount = msg.amount();
            if (amount <= 0) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());

            if (msg.isDeposit()) {
                int available = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(Items.GOLD_INGOT)) {
                        available += stack.getCount();
                    }
                }
                int toDeposit = Math.min(amount, available);
                if (toDeposit > 0) {
                    int left = toDeposit;
                    for (int i = 0; i < player.getInventory().items.size(); i++) {
                        ItemStack stack = player.getInventory().items.get(i);
                        if (stack.is(Items.GOLD_INGOT)) {
                            int take = Math.min(left, stack.getCount());
                            stack.shrink(take);
                            left -= take;
                            if (left <= 0) break;
                        }
                    }
                    player.inventoryMenu.broadcastChanges();
                    EconomyManager.addBalance(worldData, player.getUUID(), toDeposit);
                    worldData.setDirty();
                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    player.sendSystemMessage(
                            Component.literal("Внесено на баланс: ").withStyle(ChatFormatting.GREEN)
                                    .append(Component.literal("+" + String.format("%,d", (long) toDeposit).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                    .append(Component.literal(" (сдано " + toDeposit + " слитков золота)").withStyle(ChatFormatting.GRAY))
                    );
                } else {
                    player.sendSystemMessage(Component.literal("У вас нет золотых слитков в инвентаре!").withStyle(ChatFormatting.RED));
                }
            } else {
                long balance = EconomyManager.getBalance(worldData, player.getUUID());
                int toWithdraw = (int) Math.min(amount, Math.min(balance, Integer.MAX_VALUE));
                if (toWithdraw > 0) {
                    EconomyManager.deductBalance(worldData, player.getUUID(), toWithdraw);
                    int remaining = toWithdraw;
                    while (remaining > 0) {
                        int count = Math.min(remaining, 64);
                        ItemStack stack = new ItemStack(Items.GOLD_INGOT, count);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                        remaining -= count;
                    }
                    player.inventoryMenu.broadcastChanges();
                    worldData.setDirty();
                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    player.sendSystemMessage(
                            Component.literal("Снято с баланса: ").withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal("-" + String.format("%,d", (long) toWithdraw).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                                    .append(Component.literal(" (получено " + toWithdraw + " слитков золота)").withStyle(ChatFormatting.GRAY))
                    );
                } else {
                    player.sendSystemMessage(Component.literal("Недостаточно средств на балансе!").withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}
