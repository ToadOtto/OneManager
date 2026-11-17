package com.onemanager.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.onemanager.OneManager;
import com.onemanager.client.ClientPacketHandler;
import com.onemanager.data.CharacterWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.UUID;

/**
 * HUD overlay displaying the reigning Knyaz's reputation bar and square avatar icon
 * inside the custom frame ONLY while holding TAB, positioned directly below the player list.
 */
@EventBusSubscriber(modid = OneManager.MOD_ID, value = Dist.CLIENT)
public final class KnyazBossBarOverlay {

    public static final ResourceLocation KNYAZ_BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/gui/state/knyaz_bar.png");

    public static final int BAR_TEXTURE_WIDTH = 143;
    public static final int BAR_TEXTURE_HEIGHT = 32;

    private KnyazBossBarOverlay() {}

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        // Only display while the player is holding the Tab (Player List) key
        if (!mc.options.keyPlayerList.isDown()) {
            return;
        }
        // Do not render over open menus or container screens (allow ChatScreen)
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return;
        }

        CharacterWorldData worldData = ClientPacketHandler.lastKnownWorldData;
        if (worldData == null && mc.getSingleplayerServer() != null) {
            worldData = CharacterWorldData.get(mc.getSingleplayerServer());
        }
        if (worldData == null || !worldData.hasKnyaz()) {
            return;
        }

        UUID knyazUuid = worldData.getKnyazUuid();

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = guiGraphics.guiWidth();

        // Calculate horizontal position centered on screen, and vertical position below Tab player list
        int barX = (screenWidth - BAR_TEXTURE_WIDTH) / 2;
        int barY = getTabListBottomY(mc, screenWidth) + 6;

        // 1. Render Frame Texture (143x32)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        guiGraphics.blit(KNYAZ_BAR_TEXTURE, barX, barY, 0, 0, BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT, BAR_TEXTURE_WIDTH, BAR_TEXTURE_HEIGHT);

        // 2. Render Square Knyaz Avatar Head inside the icon frame (x=20..35, y=8..23, size 16x16)
        ResourceLocation skin = null;
        if (mc.getConnection() != null && knyazUuid != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(knyazUuid);
            if (info != null) {
                skin = info.getSkin().texture();
            }
        }
        if (skin == null) {
            skin = knyazUuid != null ? DefaultPlayerSkin.get(knyazUuid).texture() : DefaultPlayerSkin.getDefaultTexture();
        }
        PlayerFaceRenderer.draw(guiGraphics, skin, barX + 20, barY + 8, 16);

        // 3. Render Reputation Scale in the right groove (x=45..125, y=14..18)
        int rep = worldData.getKnyazReputation();
        int repClamped = Math.max(-100, Math.min(100, rep));
        int centerX = barX + 85;
        int halfWidth = 40;
        int targetX = centerX + (int) ((repClamped / 100.0f) * halfWidth);
        int fillY1 = barY + 14;
        int fillY2 = barY + 18;

        if (repClamped < 0) {
            // Negative: Red fill from targetX to centerX
            guiGraphics.fill(targetX, fillY1, centerX, fillY2, 0xFFE53935);
        } else if (repClamped > 0) {
            // Positive: Green fill from centerX to targetX
            guiGraphics.fill(centerX, fillY1, targetX, fillY2, 0xFF43A047);
        }

        // Center tick at 0 mark
        guiGraphics.fill(centerX, fillY1 - 1, centerX + 1, fillY2 + 1, 0xFFB0B7C6);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static int getTabListBottomY(Minecraft mc, int screenWidth) {
        int y = 10;
        int playerCount = 1;
        if (mc.getConnection() != null) {
            playerCount = Math.max(1, Math.min(80, mc.getConnection().getOnlinePlayers().size()));
        }
        int rows = playerCount;
        int cols = 1;
        while (rows > 20) {
            cols++;
            rows = (playerCount + cols - 1) / cols;
        }
        y += rows * 9 + 4;

        try {
            var tabList = mc.gui.getTabList();
            if (tabList != null) {
                for (var field : tabList.getClass().getDeclaredFields()) {
                    if (net.minecraft.network.chat.Component.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object val = field.get(tabList);
                        if (val instanceof net.minecraft.network.chat.Component comp && !comp.getString().isEmpty()) {
                            int lines = mc.font.split(comp, Math.max(100, screenWidth - 50)).size();
                            y += lines * 9 + 2;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return y;
    }
}
