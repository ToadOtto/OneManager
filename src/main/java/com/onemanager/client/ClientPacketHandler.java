package com.onemanager.client;

import com.onemanager.client.gui.CharHistoryScreen;
import com.onemanager.client.gui.RebirthScreen;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class ClientPacketHandler {
    public static CharacterWorldData lastKnownWorldData = null;

    public static void openCharHistoryScreen(CompoundTag dataTag) {
        PlayerCharacterData data = PlayerCharacterData.fromNBT(dataTag);
        CharacterWorldData worldData = new CharacterWorldData();
        if (data.getPlayerUuid() != null) {
            worldData.setPlayerData(data);
        }
        lastKnownWorldData = worldData;
        Minecraft.getInstance().setScreen(new CharHistoryScreen(worldData));
    }

    public static void handleSyncAll(CompoundTag worldDataTag, boolean openGui) {
        CharacterWorldData worldData = CharacterWorldData.load(worldDataTag);
        lastKnownWorldData = worldData;
        Minecraft mc = Minecraft.getInstance();
        if (openGui) {
            mc.setScreen(new CharHistoryScreen(worldData));
        } else if (mc.screen instanceof CharHistoryScreen screen) {
            screen.updateData(worldData);
        } else if (mc.screen instanceof com.onemanager.client.gui.note.SendNoteScreen sendScreen) {
            sendScreen.refreshRecipients();
        }
    }

    public static void openRebirthScreen(boolean isRewrite, String name, int age, String race, String appearance, String description) {
        Minecraft.getInstance().setScreen(new RebirthScreen(isRewrite, name, age, race, appearance, description, ""));
    }
}
