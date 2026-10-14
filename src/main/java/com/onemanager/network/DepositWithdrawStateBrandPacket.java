package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DepositWithdrawStateBrandPacket(boolean isDeposit, int amount) implements CustomPacketPayload {
    public static final Type<DepositWithdrawStateBrandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "brand_transfer_c2s"));

    public static final StreamCodec<ByteBuf, DepositWithdrawStateBrandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DepositWithdrawStateBrandPacket::isDeposit,
            ByteBufCodecs.VAR_INT, DepositWithdrawStateBrandPacket::amount,
            DepositWithdrawStateBrandPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DepositWithdrawStateBrandPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            int amount = msg.amount();
            if (amount <= 0) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());

            if (msg.isDeposit()) {
                int available = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(ModItems.STATE_BRAND.get())) {
                        available += stack.getCount();
                    }
                }
                int toDeposit = Math.min(amount, available);
                if (toDeposit > 0) {
                    int left = toDeposit;
                    for (int i = 0; i < player.getInventory().items.size(); i++) {
                        ItemStack stack = player.getInventory().items.get(i);
                        if (stack.is(ModItems.STATE_BRAND.get())) {
                            int take = Math.min(left, stack.getCount());
                            stack.shrink(take);
                            left -= take;
                            if (left <= 0) break;
                        }
                    }
                    player.inventoryMenu.broadcastChanges();
                    worldData.addStateBrands(player.getUUID(), toDeposit);
                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    player.sendSystemMessage(
                            Component.literal("Внесено на счёт: ").withStyle(ChatFormatting.GREEN)
                                    .append(Component.literal(toDeposit + " Гос. Марок").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    );
                } else {
                    player.sendSystemMessage(Component.literal("У вас нет предметов Гос. Марок в инвентаре!").withStyle(ChatFormatting.RED));
                }
            } else {
                int balance = worldData.getStateBrands(player.getUUID());
                int toWithdraw = Math.min(amount, balance);
                if (toWithdraw > 0) {
                    worldData.removeStateBrands(player.getUUID(), toWithdraw);
                    int remaining = toWithdraw;
                    while (remaining > 0) {
                        int count = Math.min(remaining, 64);
                        ItemStack stack = new ItemStack(ModItems.STATE_BRAND.get(), count);
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                        remaining -= count;
                    }
                    player.inventoryMenu.broadcastChanges();
                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    player.sendSystemMessage(
                            Component.literal("Снято со счёта: ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(toWithdraw + " Гос. Марок").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    );
                } else {
                    player.sendSystemMessage(Component.literal("На вашем счету нет Гос. Марок!").withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}
