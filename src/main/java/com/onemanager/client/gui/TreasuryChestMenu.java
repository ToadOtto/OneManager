package com.onemanager.client.gui;

import com.onemanager.block.entity.TreasuryChestBlockEntity;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.data.TreasuryChestEntry;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.OpenTreasuryReasonScreenS2CPacket;
import com.onemanager.network.SyncAllCharactersPacket;
import com.oneshop.item.ModItems;
import com.oneshop.item.SprotaItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TreasuryChestMenu extends AbstractContainerMenu {
    @Nullable
    private final TreasuryChestBlockEntity blockEntity;
    private final ContainerData data;
    private final SimpleContainer visualSlotContainer = new SimpleContainer(1);
    private final int initialAmount;

    public TreasuryChestMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getClientBlockEntity(playerInventory, extraData), new SimpleContainerData(2));
    }

    @Nullable
    private static TreasuryChestBlockEntity getClientBlockEntity(Inventory playerInventory, FriendlyByteBuf extraData) {
        if (extraData != null && extraData.readableBytes() >= 8 && playerInventory.player.level() != null) {
            BlockPos pos = extraData.readBlockPos();
            if (playerInventory.player.level().getBlockEntity(pos) instanceof TreasuryChestBlockEntity chestBe) {
                return chestBe;
            }
        }
        return null;
    }

    public TreasuryChestMenu(int containerId, Inventory playerInventory, @Nullable TreasuryChestBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.TREASURY_CHEST.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.initialAmount = blockEntity != null ? blockEntity.getStoredAmount() : 0;

        if (blockEntity != null && !playerInventory.player.level().isClientSide) {
            this.data.set(0, blockEntity.getStoredAmount());
            this.data.set(1, blockEntity.getMaxCapacity());
        } else if (blockEntity != null) {
            this.data.set(1, blockEntity.getMaxCapacity());
        } else {
            this.data.set(1, TreasuryChestEntry.SMALL_CAPACITY);
        }

        updateVisualSlot();

        // Slot 0: Single centered Treasury Sprota slot at (80, 35)
        this.addSlot(new Slot(visualSlotContainer, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SprotaItem;
            }

            @Override
            public boolean mayPickup(Player player) {
                return getStoredAmount() > 0;
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

        this.addDataSlots(this.data);
    }

    public int getStoredAmount() {
        return this.data.get(0);
    }

    public int getMaxCapacity() {
        int cap = this.data.get(1);
        return cap > 0 ? cap : TreasuryChestEntry.SMALL_CAPACITY;
    }

    private void setStoredAmount(int amount) {
        int clamped = Math.max(0, Math.min(getMaxCapacity(), amount));
        this.data.set(0, clamped);
        if (this.blockEntity != null && this.blockEntity.getLevel() != null && !this.blockEntity.getLevel().isClientSide) {
            this.blockEntity.setStoredAmountAndSync(clamped);
        }
        updateVisualSlot();
        this.broadcastChanges();
    }

    public void updateVisualSlot() {
        int amt = getStoredAmount();
        if (amt > 0) {
            visualSlotContainer.setItem(0, new ItemStack(ModItems.SPROTA.get(), Math.min(64, amt)));
        } else {
            visualSlotContainer.setItem(0, ItemStack.EMPTY);
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.blockEntity != null && this.blockEntity.getLevel() != null && !this.blockEntity.getLevel().isClientSide) {
            this.data.set(0, this.blockEntity.getStoredAmount());
            this.data.set(1, this.blockEntity.getMaxCapacity());
            updateVisualSlot();
        }
        super.broadcastChanges();
    }

    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        updateVisualSlot();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 0) {
            handleTreasurySlotClick(button, clickType, player);
            return;
        }
        if (clickType == ClickType.QUICK_CRAFT) {
            this.resetQuickCraft();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleTreasurySlotClick(int button, ClickType clickType, Player player) {
        int current = getStoredAmount();
        int capacity = getMaxCapacity();
        int space = Math.max(0, capacity - current);

        if (clickType == ClickType.PICKUP) {
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                if (carried.getItem() instanceof SprotaItem && space > 0) {
                    int toInsert = (button == 1) ? 1 : Math.min(carried.getCount(), space);
                    setStoredAmount(current + toInsert);
                    carried.shrink(toInsert);
                    this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
            } else if (current > 0) {
                int toTake = (button == 1) ? Math.min(32, (current + 1) / 2) : Math.min(64, current);
                setStoredAmount(current - toTake);
                this.setCarried(new ItemStack(ModItems.SPROTA.get(), toTake));
            }
        } else if (clickType == ClickType.QUICK_MOVE) {
            quickMoveStack(player, 0);
        } else if (clickType == ClickType.SWAP && button >= 0 && button < 9) {
            ItemStack hotbarStack = player.getInventory().getItem(button);
            if (!hotbarStack.isEmpty()) {
                if (hotbarStack.getItem() instanceof SprotaItem && space > 0) {
                    int toInsert = Math.min(hotbarStack.getCount(), space);
                    setStoredAmount(current + toInsert);
                    hotbarStack.shrink(toInsert);
                    player.getInventory().setItem(button, hotbarStack.isEmpty() ? ItemStack.EMPTY : hotbarStack);
                }
            } else if (current > 0) {
                int toTake = Math.min(64, current);
                setStoredAmount(current - toTake);
                player.getInventory().setItem(button, new ItemStack(ModItems.SPROTA.get(), toTake));
            }
        } else if (clickType == ClickType.THROW && current > 0) {
            int toDrop = (button == 1) ? Math.min(64, current) : 1;
            setStoredAmount(current - toDrop);
            player.drop(new ItemStack(ModItems.SPROTA.get(), toDrop), true);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == 0) {
            int current = getStoredAmount();
            if (current <= 0) {
                return ItemStack.EMPTY;
            }
            int toTake = Math.min(64, current);
            ItemStack stackToMove = new ItemStack(ModItems.SPROTA.get(), toTake);
            if (!this.moveItemStackTo(stackToMove, 1, 37, true)) {
                return ItemStack.EMPTY;
            }
            int moved = toTake - stackToMove.getCount();
            if (moved > 0) {
                setStoredAmount(current - moved);
            }
            return ItemStack.EMPTY;
        } else if (index >= 1 && index < 37) {
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasItem()) {
                ItemStack stackInSlot = slot.getItem();
                if (stackInSlot.getItem() instanceof SprotaItem) {
                    int current = getStoredAmount();
                    int space = Math.max(0, getMaxCapacity() - current);
                    if (space <= 0) {
                        return ItemStack.EMPTY;
                    }
                    int toInsert = Math.min(stackInSlot.getCount(), space);
                    setStoredAmount(current + toInsert);
                    stackInSlot.shrink(toInsert);
                    slot.setByPlayer(stackInSlot.isEmpty() ? ItemStack.EMPTY : stackInSlot);
                    return ItemStack.EMPTY;
                } else {
                    if (index >= 1 && index < 28) {
                        if (!this.moveItemStackTo(stackInSlot, 28, 37, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= 28 && index < 37 && !this.moveItemStackTo(stackInSlot, 1, 28, false)) {
                        return ItemStack.EMPTY;
                    }
                    if (stackInSlot.isEmpty()) {
                        slot.setByPlayer(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != 0 && super.canDragTo(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity != null) {
            if (!this.blockEntity.stillValid(player)) {
                return false;
            }
            if (this.blockEntity.getLevel() instanceof ServerLevel serverLevel) {
                CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
                return worldData.canAccessTreasury(player.getUUID());
            }
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = this.blockEntity.getBlockPos();
            serverLevel.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, serverLevel.random.nextFloat() * 0.1F + 0.9F);

            // Check if chest still exists at pos (not broken)
            if (serverLevel.getBlockEntity(pos) == this.blockEntity) {
                int finalAmount = this.blockEntity.getStoredAmount();
                int diff = finalAmount - this.initialAmount;

                CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
                String dim = serverLevel.dimension().location().toString();
                worldData.updateTreasuryChestAmount(dim, pos, finalAmount);

                PlayerCharacterData pd = worldData.getPlayerData(serverPlayer.getUUID());
                String charName = (pd != null && pd.hasActiveCharacter()) ? pd.getActiveCharacter().getName() : serverPlayer.getScoreboardName();
                String pName = serverPlayer.getScoreboardName();

                if (diff > 0) {
                    String formattedDiff = String.format("%,d", diff).replace(',', ' ');
                    String actionDesc = "+" + formattedDiff + " $PR (Пополнение сундука казны вручную)";
                    worldData.logTransaction(charName, pName, actionDesc, worldData.getTreasury());
                    worldData.setDirty();

                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

                    serverPlayer.sendSystemMessage(
                            Component.literal("💰 Сундук казны пополнен вручную: ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("+" + formattedDiff + " $PR")
                                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    );
                } else if (diff < 0) {
                    int withdrawn = -diff;
                    String posStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
                    worldData.setPendingTreasuryReason(serverPlayer.getUUID(),
                            new CharacterWorldData.PendingTreasuryReason(withdrawn, false, posStr,
                                    this.blockEntity.isLarge() ? "Большой Сундук Казны" : "Малый Сундук Казны"));
                    worldData.setDirty();

                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    NetworkHandler.sendToPlayer(serverPlayer, new OpenTreasuryReasonScreenS2CPacket(withdrawn, false, posStr));
                }
            }
        }
    }
}
