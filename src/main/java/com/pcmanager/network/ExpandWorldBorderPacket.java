package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.item.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExpandWorldBorderPacket(int brandsAmount) implements CustomPacketPayload {
    public static final Type<ExpandWorldBorderPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "expand_world_border_c2s"));

    public static final StreamCodec<ByteBuf, ExpandWorldBorderPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExpandWorldBorderPacket::brandsAmount,
            ExpandWorldBorderPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExpandWorldBorderPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }
            int amount = msg.brandsAmount();
            if (amount <= 0) return;

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());

            int balance = worldData.getStateBrands(player.getUUID());
            int invCount = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(ModItems.STATE_BRAND.get())) {
                    invCount += stack.getCount();
                }
            }

            int totalAvailable = balance + invCount;
            if (amount > totalAvailable) {
                player.sendSystemMessage(Component.literal("Недостаточно Гос. Марок для расширения мира!").withStyle(ChatFormatting.RED));
                return;
            }

            // Deduct from balance first, then inventory
            int fromBalance = Math.min(amount, balance);
            int fromInv = amount - fromBalance;

            if (fromBalance > 0) {
                worldData.removeStateBrands(player.getUUID(), fromBalance);
            }

            if (fromInv > 0) {
                int left = fromInv;
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
            }

            // Expand world border: 50 blocks per brand
            int blocksToAdd = amount * 50;
            ServerLevel overworld = player.server.overworld();
            WorldBorder border = overworld.getWorldBorder();
            double oldSize = border.getSize();
            double newSize = oldSize + blocksToAdd;
            border.setSize(newSize);

            // Also keep all dimensions synced
            for (ServerLevel lvl : player.server.getAllLevels()) {
                if (lvl != overworld) {
                    lvl.getWorldBorder().setSize(newSize);
                }
            }

            // Broadcast sound and message
            for (ServerPlayer sp : player.server.getPlayerList().getPlayers()) {
                sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
                sp.sendSystemMessage(
                        Component.literal("🌍 ").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" расширил(а) границы Мира на ").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("+" + blocksToAdd + " блоков").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                                .append(Component.literal("! (Текущий размер: " + (long) newSize + " блоков)").withStyle(ChatFormatting.GRAY))
                );
            }

            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
        });
    }
}
