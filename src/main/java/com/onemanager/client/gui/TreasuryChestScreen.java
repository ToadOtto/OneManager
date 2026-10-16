package com.onemanager.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class TreasuryChestScreen extends AbstractContainerScreen<TreasuryChestMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png");

    public TreasuryChestScreen(TreasuryChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw exact stored count over Slot 0 if > 64
        int stored = this.menu.getStoredAmount();
        if (stored > 1) {
            String countStr = String.valueOf(stored);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
            // Cover vanilla '64' text on the slot and draw actual stored count
            Slot slot0 = this.menu.getSlot(0);
            int sx = x + slot0.x;
            int sy = y + slot0.y;
            float scale = countStr.length() >= 4 ? 0.75f : 1.0f;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(sx + 16, sy + 16, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            int textW = this.font.width(countStr);
            guiGraphics.drawString(this.font, countStr, -textW + 1, -this.font.lineHeight + 2, 0xFFFFAA00, true);
            guiGraphics.pose().popPose();
            guiGraphics.pose().popPose();
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlotContents(GuiGraphics guiGraphics, net.minecraft.world.item.ItemStack itemstack, Slot slot, String countString) {
        if (slot.index == 0 && this.menu.getStoredAmount() > 1) {
            // Suppress default '64' count string so our custom count string renders cleanly
            super.renderSlotContents(guiGraphics, itemstack, slot, "");
        } else {
            super.renderSlotContents(guiGraphics, itemstack, slot, countString);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        List<Component> list = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.hoveredSlot != null && this.hoveredSlot.index == 0) {
            int stored = this.menu.getStoredAmount();
            int cap = this.menu.getMaxCapacity();
            list.add(Component.literal("В сундуке: " + String.format("%,d", stored).replace(',', ' ') + " / " + String.format("%,d", cap).replace(',', ' ') + " $PR")
                    .withStyle(ChatFormatting.GOLD));
            list.add(Component.literal("ЛКМ / Shift+ЛКМ — положить или забрать до 64 $PR").withStyle(ChatFormatting.GRAY));
            list.add(Component.literal("ПКМ — положить 1 $PR или забрать до 32 $PR").withStyle(ChatFormatting.DARK_GRAY));
        }
        return list;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw base container background
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Cover the 3x3 dispenser grid with smooth container background color (0xFFC6C6C6)
        guiGraphics.fill(x + 60, y + 16, x + 116, y + 71, 0xFFC6C6C6);

        // Draw single centered slot frame at (79, 34) for slot at (80, 35) with golden-accented frame
        guiGraphics.fill(x + 78, y + 33, x + 98, y + 53, 0xFF8B7332);
        guiGraphics.fill(x + 79, y + 34, x + 97, y + 35, 0xFF373737);
        guiGraphics.fill(x + 79, y + 34, x + 80, y + 51, 0xFF373737);
        guiGraphics.fill(x + 80, y + 51, x + 97, y + 52, 0xFFFFFFFF);
        guiGraphics.fill(x + 96, y + 35, x + 97, y + 52, 0xFFFFFFFF);
        guiGraphics.fill(x + 80, y + 35, x + 96, y + 51, 0xFF8B8B8B);

        // Progress bar below the slot
        int stored = this.menu.getStoredAmount();
        int cap = Math.max(1, this.menu.getMaxCapacity());
        int barW = 100;
        int barX = x + (this.imageWidth - barW) / 2;
        int barY = y + 57;
        guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, 0xFF373737);
        guiGraphics.fill(barX, barY, barX + barW, barY + 4, 0xFF1E1E1E);
        int fillW = Math.min(barW, (int) ((long) stored * barW / cap));
        if (fillW > 0) {
            guiGraphics.fill(barX, barY, barX + fillW, barY + 4, 0xFFD4AF37);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        int stored = this.menu.getStoredAmount();
        int cap = this.menu.getMaxCapacity();
        String statusStr = String.format("%,d / %,d $PR", stored, cap).replace(',', ' ');
        int statusW = this.font.width(statusStr);
        guiGraphics.drawString(this.font, statusStr, (this.imageWidth - statusW) / 2, 20, 0x3A2E0D, false);
    }
}
