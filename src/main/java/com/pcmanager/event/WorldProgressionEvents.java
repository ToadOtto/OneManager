package com.pcmanager.event;

import com.pcmanager.PCManager;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.network.NetworkHandler;
import com.pcmanager.network.SyncAllCharactersPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

@EventBusSubscriber(modid = PCManager.MOD_ID)
public class WorldProgressionEvents {

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String advId = event.getAdvancement().id().toString();
            // Ignore internal recipe unlock advancements
            if (advId.contains("recipe") || advId.startsWith("minecraft:recipes/")) {
                return;
            }

            CharacterWorldData worldData = CharacterWorldData.get(player.server);
            if (worldData.recordAdvancement(advId)) {
                int newLevel = worldData.getWorldLevel();
                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                // Every 25 levels milestone announcement
                if (newLevel % 25 == 0 && newLevel <= 200) {
                    for (ServerPlayer sp : player.server.getPlayerList().getPlayers()) {
                        sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
                        sp.sendSystemMessage(
                                Component.literal("🌟 [Мир] Достигнут рубеж: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                        .append(Component.literal("Уровень " + newLevel + "!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                        );
                    }
                }
            }
        }
    }
}
