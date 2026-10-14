package com.onemanager.network;

import com.onemanager.OneManager;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.profession.Profession;
import com.onemanager.profession.ProfessionCategory;
import com.onemanager.profession.ProfessionRegistry;
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

public record SelectProfessionPacket(int professionId) implements CustomPacketPayload {
    public static final Type<SelectProfessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "select_profession_c2s"));

    public static final StreamCodec<ByteBuf, SelectProfessionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectProfessionPacket::professionId,
            SelectProfessionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectProfessionPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player p = context.player();
            if (!(p instanceof ServerPlayer player)) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            worldData.updatePlayerName(player.getUUID(), player.getName().getString());
            PlayerCharacterData pd = worldData.getOrCreatePlayerData(player.getUUID());

            if (!pd.hasActiveCharacter()) {
                player.sendSystemMessage(Component.literal("У вас нет активного живого персонажа!").withStyle(ChatFormatting.RED));
                return;
            }

            Profession prof = ProfessionRegistry.getById(msg.professionId());
            if (prof == null) {
                player.sendSystemMessage(Component.literal("Неизвестная профессия: #" + msg.professionId()).withStyle(ChatFormatting.RED));
                return;
            }

            if (pd.hasProfession(prof.getTitle()) || pd.hasProfession(String.valueOf(prof.getId()))) {
                player.sendSystemMessage(Component.literal("У вашего персонажа уже есть профессия: " + prof.getTitle()).withStyle(ChatFormatting.YELLOW));
                return;
            }

            // Check profession occupancy on the server
            int occupied = 0;
            for (PlayerCharacterData other : worldData.getAllPlayerData().values()) {
                if (other.hasActiveCharacter() && (other.hasProfession(prof.getTitle()) || other.hasProfession(String.valueOf(prof.getId())))) {
                    occupied++;
                }
            }

            if (occupied >= prof.getMaxOccupants()) {
                player.sendSystemMessage(Component.literal("Все места для профессии \"" + prof.getTitle() + "\" уже заняты (" + occupied + "/" + prof.getMaxOccupants() + ")!").withStyle(ChatFormatting.RED));
                return;
            }

            if (prof.getType() == ProfessionCategory.STATE) {
                player.sendSystemMessage(Component.literal("Государственные должности назначаются правителем!").withStyle(ChatFormatting.RED));
                return;
            }

            // Check player available slots
            boolean isMain = prof.getType() == ProfessionCategory.MAIN;
            int mainSlots = pd.getMainProfessionSlots();
            int secSlots = pd.getSecondaryProfessionSlots();

            int mainUsed = 0;
            int secUsed = 0;
            for (String userProf : pd.getProfessions()) {
                Profession registered = ProfessionRegistry.getByIdOrTitle(userProf);
                if (registered != null) {
                    if (registered.getType() == ProfessionCategory.MAIN) {
                        mainUsed++;
                    } else {
                        secUsed++;
                    }
                } else {
                    if (mainUsed < mainSlots) {
                        mainUsed++;
                    } else {
                        secUsed++;
                    }
                }
            }

            if (isMain && mainUsed >= mainSlots) {
                player.sendSystemMessage(Component.literal("У вас нет свободных слотов основных профессий (" + mainUsed + "/" + mainSlots + ")!").withStyle(ChatFormatting.RED));
                return;
            }

            if (!isMain && secUsed >= secSlots) {
                player.sendSystemMessage(Component.literal("У вас нет свободных слотов дополнительных профессий (" + secUsed + "/" + secSlots + ")!").withStyle(ChatFormatting.RED));
                return;
            }

            // Assign profession
            pd.addProfession(prof.getTitle());
            worldData.setDirty();

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            ChatFormatting color = isMain ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            player.sendSystemMessage(
                    Component.literal("Вы успешно освоили профессию: ")
                            .withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(prof.getTitle()).withStyle(color, ChatFormatting.BOLD))
                            .append(Component.literal(" (" + prof.getType().getDisplayName() + ")").withStyle(ChatFormatting.GRAY))
            );
        });
    }
}
