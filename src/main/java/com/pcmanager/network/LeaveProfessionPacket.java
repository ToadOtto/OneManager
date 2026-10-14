package com.pcmanager.network;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.PlayerCharacterData;
import com.pcmanager.economy.EconomyManager;
import com.pcmanager.profession.Profession;
import com.pcmanager.profession.ProfessionCategory;
import com.pcmanager.profession.ProfessionRegistry;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LeaveProfessionPacket(int professionId) implements CustomPacketPayload {
    public static final Type<LeaveProfessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PCManager.MOD_ID, "leave_profession_c2s"));

    public static final StreamCodec<ByteBuf, LeaveProfessionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LeaveProfessionPacket::professionId,
            LeaveProfessionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeaveProfessionPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player) || player.server == null) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());
            PlayerCharacterData pd = worldData.getOrCreatePlayerData(player.getUUID());

            if (!pd.hasActiveCharacter()) {
                player.sendSystemMessage(Component.literal("У вас нет активного персонажа!").withStyle(ChatFormatting.RED));
                return;
            }

            Profession prof = ProfessionRegistry.getById(msg.professionId());
            if (prof == null) {
                player.sendSystemMessage(Component.literal("Неизвестная профессия: #" + msg.professionId()).withStyle(ChatFormatting.RED));
                return;
            }

            if (!pd.hasProfession(prof.getTitle()) && !pd.hasProfession(String.valueOf(prof.getId()))) {
                player.sendSystemMessage(Component.literal("У вашего персонажа нет профессии: " + prof.getTitle()).withStyle(ChatFormatting.YELLOW));
                return;
            }

            // Trigger long cycle check with weekly grants
            pd.triggerBrandCycleReset(System.currentTimeMillis(), true);

            // Category and cycle checks
            ProfessionCategory cat = prof.getType();
            if (cat == ProfessionCategory.MAIN) {
                if (pd.hasLeftMainThisCycle()) {
                    long remainingMs = pd.getLongCycleRemainingMs();
                    long hrs = remainingMs / 3600000L;
                    long mins = (remainingMs % 3600000L) / 60000L;
                    player.sendSystemMessage(Component.literal("❌ Вы уже покидали основную профессию в этом недельном цикле! Сброс через: " + hrs + "ч " + mins + "м.").withStyle(ChatFormatting.RED));
                    return;
                }
                long balance = EconomyManager.getBalance(worldData, player.getUUID());
                int brands = pd.getStateBrands();
                if (balance < 500 || brands < 2) {
                    player.sendSystemMessage(Component.literal("❌ Недостаточно ресурсов для ухода из основной профессии! Требуется 500 $PR и 2 Гос. Марки (у вас: " + balance + " $PR, " + brands + " марок).").withStyle(ChatFormatting.RED));
                    return;
                }
                EconomyManager.deductBalance(worldData, player.getUUID(), 500);
                pd.removeStateBrands(2);
                pd.setLeftMainThisCycle(true);
            } else if (cat == ProfessionCategory.SECONDARY) {
                if (pd.hasLeftSecondaryThisCycle()) {
                    long remainingMs = pd.getLongCycleRemainingMs();
                    long hrs = remainingMs / 3600000L;
                    long mins = (remainingMs % 3600000L) / 60000L;
                    player.sendSystemMessage(Component.literal("❌ Вы уже покидали дополнительную профессию в этом недельном цикле! Сброс через: " + hrs + "ч " + mins + "м.").withStyle(ChatFormatting.RED));
                    return;
                }
                long balance = EconomyManager.getBalance(worldData, player.getUUID());
                int brands = pd.getStateBrands();
                if (balance < 250 || brands < 1) {
                    player.sendSystemMessage(Component.literal("❌ Недостаточно ресурсов для ухода из дополнительной профессии! Требуется 250 $PR и 1 Гос. Марка (у вас: " + balance + " $PR, " + brands + " марок).").withStyle(ChatFormatting.RED));
                    return;
                }
                EconomyManager.deductBalance(worldData, player.getUUID(), 250);
                pd.removeStateBrands(1);
                pd.setLeftSecondaryThisCycle(true);
            }

            pd.removeProfession(prof.getTitle());
            pd.removeProfession(String.valueOf(prof.getId()));
            pd.removeProfession(prof.getIdString());

            worldData.setDirty();
            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            Component successMsg = Component.literal("✔ Вы покинули профессию: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(prof.getTitle()).withStyle(ChatFormatting.WHITE));
            if (cat == ProfessionCategory.MAIN) {
                successMsg = successMsg.copy().append(Component.literal(" (списано 500 $PR и 2 марки, лимит на неделю исчерпан)").withStyle(ChatFormatting.GRAY));
            } else if (cat == ProfessionCategory.SECONDARY) {
                successMsg = successMsg.copy().append(Component.literal(" (списано 250 $PR и 1 марка, лимит на неделю исчерпан)").withStyle(ChatFormatting.GRAY));
            } else {
                successMsg = successMsg.copy().append(Component.literal(" (бесплатно)").withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(successMsg);
        });
    }
}
