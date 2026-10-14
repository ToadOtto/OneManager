package com.onemanager.client.gui;

import com.onemanager.item.RemainsItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CremationFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public CremationFurnaceMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(4), new SimpleContainerData(3));
    }

    public CremationFurnaceMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.CREMATION_FURNACE.get(), containerId);
        checkContainerSize(container, 4);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);

        // Slot 0: Remains input slot (only accepts remains)
        this.addSlot(new Slot(container, 0, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RemainsItem.isRemains(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 1: Fuel slot 1 (only accepts lava bucket)
        this.addSlot(new Slot(container, 1, 45, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAVA_BUCKET);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 2: Fuel slot 2 (only accepts lava bucket)
        this.addSlot(new Slot(container, 2, 67, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAVA_BUCKET);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 3: Output slot (ashes, take-only)
        this.addSlot(new Slot(container, 3, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Player Inventory (3 rows x 9 columns)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player Hotbar (9 columns)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    public int getCookingProgress() {
        return this.data.get(0);
    }

    public int getCookingTotalTime() {
        int total = this.data.get(1);
        return total == 0 ? 6000 : total;
    }

    public boolean isLit() {
        return this.data.get(2) != 0;
    }

    public int getBurnProgressScaled(int pixels) {
        int progress = getCookingProgress();
        int total = getCookingTotalTime();
        if (total <= 0 || progress <= 0) return 0;
        return Math.min(pixels, (int) Math.ceil((double) progress * pixels / total));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index == 3) { // Output slot
                if (!this.moveItemStackTo(itemstack1, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            } else if (index >= 0 && index < 4) { // Furnace slots
                if (!this.moveItemStackTo(itemstack1, 4, 40, false)) {
                    return ItemStack.EMPTY;
                }
            } else { // Player inventory
                if (RemainsItem.isRemains(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.is(Items.LAVA_BUCKET)) {
                    if (!this.moveItemStackTo(itemstack1, 1, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 4 && index < 31) {
                    if (!this.moveItemStackTo(itemstack1, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 31 && index < 40 && !this.moveItemStackTo(itemstack1, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
