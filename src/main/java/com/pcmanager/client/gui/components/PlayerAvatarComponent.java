package com.pcmanager.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * An owo-ui component that renders the player head/skin for a given UUID.
 * Supports dimming via brightness parameter (e.g. 0.70f for 30% darkening).
 */
public class PlayerAvatarComponent extends BaseComponent {
    private final UUID playerUuid;
    private final int size;
    private final float brightness;

    public PlayerAvatarComponent(UUID playerUuid, int size) {
        this(playerUuid, size, 1.0f);
    }

    public PlayerAvatarComponent(UUID playerUuid, int size, float brightness) {
        this.playerUuid = playerUuid;
        this.size = size;
        this.brightness = brightness;
        this.sizing(Sizing.fixed(size), Sizing.fixed(size));
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        ResourceLocation skin = null;
        if (Minecraft.getInstance().getConnection() != null && playerUuid != null) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                skin = info.getSkin().texture();
            }
        }
        if (skin == null) {
            skin = playerUuid != null ? DefaultPlayerSkin.get(playerUuid).texture() : DefaultPlayerSkin.getDefaultTexture();
        }

        // Render black background & subtle outline
        context.fill(this.x - 1, this.y - 1, this.x + this.size + 1, this.y + this.size + 1, 0xFF181B22);

        if (brightness < 1.0f) {
            RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0f);
        }
        PlayerFaceRenderer.draw(context, skin, this.x, this.y, this.size);
        if (brightness < 1.0f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
