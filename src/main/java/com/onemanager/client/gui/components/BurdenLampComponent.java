package com.onemanager.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.onemanager.OneManager;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * An owo-ui component representing the Ancestor Burden lamp indicator.
 * Glows brightly when burdened and dims when relieved.
 */
public class BurdenLampComponent extends BaseComponent {
    public static final ResourceLocation ICON_BURDEN = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/mob_effect/ancestor_burden.png");
    private final boolean active;
    private final int size;

    public BurdenLampComponent(boolean active, int size) {
        this.active = active;
        this.size = size;
        this.sizing(Sizing.fixed(size), Sizing.fixed(size));

        if (active) {
            this.tooltip(Component.literal("Бремя Предков: Активно").withStyle(ChatFormatting.RED));
        } else {
            this.tooltip(Component.literal("Бремя Предков: Снято").withStyle(ChatFormatting.GRAY));
        }
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (active) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShaderColor(0.25f, 0.25f, 0.28f, 0.40f);
        }

        context.blit(ICON_BURDEN, this.x, this.y, size, size, 0.0f, 0.0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
