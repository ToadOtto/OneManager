package com.onemanager.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.onemanager.OneManager;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.RequestCharHistoryC2SPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Handles client-side keybindings for OneManager.
 * Registers the keybinding to open the character history screen (/charhistory).
 */
@EventBusSubscriber(modid = OneManager.MOD_ID, value = Dist.CLIENT)
public class KeyBindings {
    public static final String KEY_CATEGORY = "key.categories.onemanager";
    public static final String KEY_OPEN_CHAR_HISTORY = "key.onemanager.open_charhistory";

    public static final KeyMapping OPEN_CHAR_HISTORY = new KeyMapping(
            KEY_OPEN_CHAR_HISTORY,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    public static void register(IEventBus modBus) {
        modBus.addListener(KeyBindings::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAR_HISTORY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            while (OPEN_CHAR_HISTORY.consumeClick()) {
                NetworkHandler.sendToServer(new RequestCharHistoryC2SPacket());
            }
        }
    }
}
