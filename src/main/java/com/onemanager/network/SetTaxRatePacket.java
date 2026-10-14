package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
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

public record SetTaxRatePacket(int newTaxRate) implements CustomPacketPayload {
    public static final Type<SetTaxRatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "set_tax_rate"));

    public static final StreamCodec<ByteBuf, SetTaxRatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetTaxRatePacket::newTaxRate,
            SetTaxRatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTaxRatePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (worldData.canManageTaxes(player.getUUID()) || player.hasPermissions(2)) {
                int rate = Math.max(0, payload.newTaxRate());
                worldData.setTaxRate(rate);
                worldData.setDirty();

                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                player.server.getPlayerList().broadcastSystemMessage(
                        Component.literal("⚖ Ставка налога установлена: ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(rate + " $PR").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                .append(Component.literal(" каждые 20 минут.").withStyle(ChatFormatting.GRAY)),
                        false
                );
            } else {
                player.sendSystemMessage(
                        Component.literal("❌ Только Князь может изменять налог!").withStyle(ChatFormatting.RED)
                );
            }
        });
    }
}
