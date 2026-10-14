package com.pcmanager.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CremationFurnaceScreen extends AbstractContainerScreen<CremationFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/blast_furnace.png");
    private static final ResourceLocation LIT_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/blast_furnace/lit_progress");
    private static final ResourceLocation BURN_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/blast_furnace/burn_progress");

    public CremationFurnaceScreen(CremationFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Hover tooltip over fuel slots when furnace is waiting for fuel
        if (!this.menu.isLit() && !this.menu.getSlot(1).hasItem() && !this.menu.getSlot(2).hasItem()) {
            if (mouseX >= x + 44 && mouseX <= x + 85 && mouseY >= y + 52 && mouseY <= y + 70) {
                guiGraphics.renderTooltip(this.font, Component.literal("Топливо: ровно 2 ведра лавы").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
            }
        }

        // Hover tooltip over progress arrow showing remaining time and percentage
        if (mouseX >= x + 79 && mouseX <= x + 103 && mouseY >= y + 34 && mouseY <= y + 50) {
            if (this.menu.isLit()) {
                int progress = this.menu.getCookingProgress();
                int total = this.menu.getCookingTotalTime();
                int percent = total > 0 ? (progress * 100 / total) : 0;
                int remainingTicks = Math.max(0, total - progress);
                int remainingSec = remainingTicks / 20;
                int min = remainingSec / 60;
                int sec = remainingSec % 60;
                guiGraphics.renderTooltip(this.font, Component.literal(String.format("Кремация: %d%% (осталось %d:%02d)", percent, min, sec)).withStyle(ChatFormatting.GOLD), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Base blast furnace background
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Draw dual fuel slot borders
        // Left fuel slot (45, 53) -> box at (44, 52)
        guiGraphics.fill(x + 44, y + 52, x + 62, y + 70, 0xFF8B8B8B);
        guiGraphics.fill(x + 44, y + 52, x + 61, y + 53, 0xFF373737);
        guiGraphics.fill(x + 44, y + 52, x + 45, y + 69, 0xFF373737);
        guiGraphics.fill(x + 45, y + 69, x + 62, y + 70, 0xFFFFFFFF);
        guiGraphics.fill(x + 61, y + 53, x + 62, y + 70, 0xFFFFFFFF);
        guiGraphics.fill(x + 45, y + 53, x + 61, y + 69, 0xFF8B8B8B);

        // Right fuel slot (67, 53) -> box at (66, 52)
        guiGraphics.fill(x + 66, y + 52, x + 84, y + 70, 0xFF8B8B8B);
        guiGraphics.fill(x + 66, y + 52, x + 83, y + 53, 0xFF373737);
        guiGraphics.fill(x + 66, y + 52, x + 67, y + 69, 0xFF373737);
        guiGraphics.fill(x + 67, y + 69, x + 84, y + 70, 0xFFFFFFFF);
        guiGraphics.fill(x + 83, y + 53, x + 84, y + 70, 0xFFFFFFFF);
        guiGraphics.fill(x + 67, y + 53, x + 83, y + 69, 0xFF8B8B8B);

        // Flame icon if lit
        if (this.menu.isLit()) {
            guiGraphics.blitSprite(LIT_PROGRESS_SPRITE, 14, 14, 0, 0, x + 56, y + 36, 14, 14);
        }

        // Cooking arrow progress
        int progressScaled = this.menu.getBurnProgressScaled(24);
        if (progressScaled > 0) {
            guiGraphics.blitSprite(BURN_PROGRESS_SPRITE, 24, 16, 0, 0, x + 79, y + 34, progressScaled, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }
}
