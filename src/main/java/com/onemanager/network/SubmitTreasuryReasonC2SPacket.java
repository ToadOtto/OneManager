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

public record SubmitTreasuryReasonC2SPacket(String reason) implements CustomPacketPayload {
    public static final Type<SubmitTreasuryReasonC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "submit_treasury_reason"));

    public static final StreamCodec<ByteBuf, SubmitTreasuryReasonC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SubmitTreasuryReasonC2SPacket::reason,
            SubmitTreasuryReasonC2SPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitTreasuryReasonC2SPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            String cleanReason = payload.reason() != null ? payload.reason().trim() : "";
            if (cleanReason.isEmpty()) {
                cleanReason = "Не указана";
            }

            boolean logged = worldData.completePendingTreasuryReason(player, cleanReason);
            if (logged) {
                worldData.setDirty();
                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                player.sendSystemMessage(Component.literal("📜 Запись внесена в Лог Казны (Причина: " + cleanReason + ")")
                        .withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
