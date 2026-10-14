package com.onemanager.client.gui;

import com.onemanager.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TombWorkbenchScreen extends AbstractContainerScreen<TombWorkbenchMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final ResourceLocation RECIPE_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final ResourceLocation RECIPE_SPRITE = ResourceLocation.withDefaultNamespace("container/stonecutter/recipe");

    private final ItemStack tombstoneDisplay = new ItemStack(ModItems.TOMBSTONE.get());

    public TombWorkbenchScreen(TombWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Hover tooltip over input slot when empty
        if (!this.menu.getSlot(TombWorkbenchMenu.INPUT_SLOT).hasItem()) {
            if (mouseX >= x + 19 && mouseX <= x + 37 && mouseY >= y + 32 && mouseY <= y + 50) {
                guiGraphics.renderTooltip(this.font, Component.literal("Положите 16 блоков камня").withStyle(ChatFormatting.GRAY), mouseX, mouseY);
            }
        }

        // Hover tooltip over center recipe card
        if (mouseX >= x + 79 && mouseX <= x + 97 && mouseY >= y + 32 && mouseY <= y + 50) {
            ItemStack input = this.menu.getSlot(TombWorkbenchMenu.INPUT_SLOT).getItem();
            int current = (input.is(Items.STONE)) ? input.getCount() : 0;
            if (current >= TombWorkbenchMenu.STONE_REQUIRED) {
                guiGraphics.renderTooltip(this.font, Component.literal("Высечь Могилу (16 камня)").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal(String.format("Требуется: 16 камня (в наличии: %d/16)", current)).withStyle(ChatFormatting.RED), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Base stonecutter background
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Center crafting display in the stonecutter recipe area (between input and output)
        ItemStack input = this.menu.getSlot(TombWorkbenchMenu.INPUT_SLOT).getItem();
        boolean hasEnoughStone = input.is(Items.STONE) && input.getCount() >= TombWorkbenchMenu.STONE_REQUIRED;

        // Draw recipe button in center (80, 32)
        ResourceLocation buttonSprite = hasEnoughStone ? RECIPE_SELECTED_SPRITE : RECIPE_SPRITE;
        guiGraphics.blitSprite(buttonSprite, 16, 18, 0, 0, x + 80, y + 32, 16, 18);

        // Draw Tombstone item inside the button
        guiGraphics.renderFakeItem(tombstoneDisplay, x + 80, y + 33);

        // Cost indicator below the recipe card
        String costText = "16x";
        int costWidth = this.font.width(costText);
        guiGraphics.drawString(this.font, costText, x + 88 - (costWidth / 2), y + 53, hasEnoughStone ? 0x2E7D32 : 0xC62828, false);
    }
}
