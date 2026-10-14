package com.onemanager.client.gui;

import com.onemanager.block.ModBlocks;
import com.onemanager.item.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TombWorkbenchMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    public static final int STONE_REQUIRED = 16;

    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END = 38;

    private final ContainerLevelAccess access;
    final Slot inputSlot;
    final Slot resultSlot;

    public final Container inputContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TombWorkbenchMenu.this.slotsChanged(this);
        }
    };

    final ResultContainer resultContainer = new ResultContainer();

    public TombWorkbenchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public TombWorkbenchMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(ModMenuTypes.TOMB_WORKBENCH.get(), containerId);
        this.access = access;

        // Input slot for Stone (matches stonecutter.png slot at 20, 33)
        this.inputSlot = this.addSlot(new Slot(this.inputContainer, INPUT_SLOT, 20, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.STONE);
            }
        });

        // Output slot for Tombstone (matches stonecutter.png slot at 143, 33)
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, RESULT_SLOT, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                stack.onCraftedBy(player.level(), player, stack.getCount());
                ItemStack input = TombWorkbenchMenu.this.inputSlot.getItem();
                if (!input.isEmpty() && input.is(Items.STONE)) {
                    TombWorkbenchMenu.this.inputSlot.remove(STONE_REQUIRED);
                }
                TombWorkbenchMenu.this.setupResultSlot();

                access.execute((level, pos) -> {
                    level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                });

                super.onTake(player, stack);
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
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.inputContainer) {
            this.setupResultSlot();
        }
    }

    public void setupResultSlot() {
        ItemStack input = this.inputSlot.getItem();
        if (!input.isEmpty() && input.is(Items.STONE) && input.getCount() >= STONE_REQUIRED) {
            this.resultSlot.set(new ItemStack(ModItems.TOMBSTONE.get()));
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.TOMB_WORKBENCH.get());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index == RESULT_SLOT) {
                // Craft as many as possible until inventory full or less than 16 stones
                while (this.inputSlot.getItem().is(Items.STONE) && this.inputSlot.getItem().getCount() >= STONE_REQUIRED) {
                    ItemStack crafted = new ItemStack(ModItems.TOMBSTONE.get());
                    if (!this.moveItemStackTo(crafted, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
                        break;
                    }
                    crafted.getItem().onCraftedBy(crafted, player.level(), player);
                    this.inputSlot.remove(STONE_REQUIRED);
                    this.access.execute((level, pos) -> {
                        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    });
                }
                this.setupResultSlot();
                return ItemStack.EMPTY;
            } else if (index == INPUT_SLOT) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else { // Player inventory
                if (itemstack1.is(Items.STONE)) {
                    if (!this.moveItemStackTo(itemstack1, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                    if (!this.moveItemStackTo(itemstack1, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END && !this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, false)) {
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
            this.setupResultSlot();
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(RESULT_SLOT);
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputContainer));
    }
}
