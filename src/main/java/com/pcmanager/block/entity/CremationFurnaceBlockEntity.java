package com.pcmanager.block.entity;

import com.pcmanager.block.CremationFurnaceBlock;
import com.pcmanager.client.gui.CremationFurnaceMenu;
import com.pcmanager.item.AshesItem;
import com.pcmanager.item.RemainsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class CremationFurnaceBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int TOTAL_CREMATION_TIME = 6000; // 5 minutes = 300s = 6000 ticks
    public static final int INCINERATOR_SLOT = 4;
    private static final int[] HOPPER_SLOTS = new int[]{INCINERATOR_SLOT};

    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private int cookingProgress = 0;
    private int cookingTotalTime = TOTAL_CREMATION_TIME;
    private boolean isLit = false;
    private long lastIncinerateSoundTime = -1L;
    private long lastTickGameTime = -1L;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> CremationFurnaceBlockEntity.this.cookingProgress;
                case 1 -> CremationFurnaceBlockEntity.this.cookingTotalTime;
                case 2 -> CremationFurnaceBlockEntity.this.isLit ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> CremationFurnaceBlockEntity.this.cookingProgress = value;
                case 1 -> CremationFurnaceBlockEntity.this.cookingTotalTime = value;
                case 2 -> CremationFurnaceBlockEntity.this.isLit = (value != 0);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CremationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREMATION_FURNACE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CremationFurnaceBlockEntity entity) {
        boolean dirty = false;
        long currentGameTime = level.getGameTime();

        if (entity.isLit) {
            ItemStack input = entity.items.get(0);
            if (input.isEmpty() || !RemainsItem.isRemains(input)) {
                // Input removed during cremation
                entity.isLit = false;
                entity.cookingProgress = 0;
                entity.lastTickGameTime = currentGameTime;
                dirty = true;
            } else {
                int delta = 1;
                if (entity.lastTickGameTime > 0 && currentGameTime > entity.lastTickGameTime + 1) {
                    long missed = currentGameTime - entity.lastTickGameTime - 1;
                    delta += (int) Math.min(missed, (long) (entity.cookingTotalTime - entity.cookingProgress));
                }
                entity.lastTickGameTime = currentGameTime;
                entity.cookingProgress += delta;

                if (entity.cookingProgress >= entity.cookingTotalTime) {
                    // Complete cremation
                    ItemStack ashes = AshesItem.createAshes(input);
                    entity.items.set(3, ashes);
                    entity.items.set(0, ItemStack.EMPTY);
                    entity.cookingProgress = 0;
                    entity.isLit = false;
                    dirty = true;

                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 15, 0.2D, 0.2D, 0.2D, 0.05D);
                    }
                } else if (entity.cookingProgress % 20 == 0 || delta > 1) {
                    dirty = true;
                }
            }
        } else {
            entity.lastTickGameTime = currentGameTime;
            // Check if can start: exactly 2 lava buckets and remains
            ItemStack input = entity.items.get(0);
            ItemStack fuel1 = entity.items.get(1);
            ItemStack fuel2 = entity.items.get(2);
            ItemStack output = entity.items.get(3);

            if (RemainsItem.isRemains(input) && fuel1.is(Items.LAVA_BUCKET) && fuel2.is(Items.LAVA_BUCKET) && output.isEmpty()) {
                // Consume the 2 lava buckets, turning them into empty buckets
                entity.items.set(1, new ItemStack(Items.BUCKET));
                entity.items.set(2, new ItemStack(Items.BUCKET));
                entity.cookingProgress = 0;
                entity.cookingTotalTime = TOTAL_CREMATION_TIME;
                entity.isLit = true;
                dirty = true;

                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        boolean currentBlockLit = state.hasProperty(CremationFurnaceBlock.LIT) && state.getValue(CremationFurnaceBlock.LIT);
        if (currentBlockLit != entity.isLit) {
            level.setBlock(pos, state.setValue(CremationFurnaceBlock.LIT, entity.isLit), 3);
            dirty = true;
        }

        if (dirty) {
            entity.setChanged();
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void incinerateItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (this.level != null && !this.level.isClientSide) {
            long time = this.level.getGameTime();
            if (time != this.lastIncinerateSoundTime) {
                this.lastIncinerateSoundTime = time;
                this.level.playSound(
                        null,
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D,
                        SoundEvents.GENERIC_BURN,
                        SoundSource.BLOCKS,
                        0.5F,
                        2.0F + this.level.random.nextFloat() * 0.4F
                );

                if (this.level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.LAVA,
                            this.worldPosition.getX() + 0.5D,
                            this.worldPosition.getY() + 0.8D,
                            this.worldPosition.getZ() + 0.5D,
                            3,
                            0.15D, 0.1D, 0.15D, 0.02D
                    );
                    serverLevel.sendParticles(
                            ParticleTypes.SMOKE,
                            this.worldPosition.getX() + 0.5D,
                            this.worldPosition.getY() + 0.8D,
                            this.worldPosition.getZ() + 0.5D,
                            4,
                            0.15D, 0.1D, 0.15D, 0.01D
                    );
                }
            }
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == INCINERATOR_SLOT) {
            return ItemStack.EMPTY;
        }
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == INCINERATOR_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == INCINERATOR_SLOT) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == INCINERATOR_SLOT) {
            if (!stack.isEmpty()) {
                incinerateItem(stack);
            }
            this.items.set(INCINERATOR_SLOT, ItemStack.EMPTY);
            return;
        }
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == INCINERATOR_SLOT) {
            return true;
        }
        if (slot == 0) {
            return RemainsItem.isRemains(stack);
        }
        if (slot == 1 || slot == 2) {
            return stack.is(Items.LAVA_BUCKET);
        }
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return HOPPER_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return slot == INCINERATOR_SLOT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                incinerateItem(stack);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    };

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return itemHandler;
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.pcmanager.cremation_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CremationFurnaceMenu(containerId, playerInventory, this, this.dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("CookingProgress", this.cookingProgress);
        tag.putInt("CookingTotalTime", this.cookingTotalTime);
        tag.putBoolean("IsLit", this.isLit);
        tag.putLong("LastTickGameTime", this.lastTickGameTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.items.set(INCINERATOR_SLOT, ItemStack.EMPTY);
        this.cookingProgress = tag.getInt("CookingProgress");
        this.cookingTotalTime = tag.contains("CookingTotalTime") ? tag.getInt("CookingTotalTime") : TOTAL_CREMATION_TIME;
        this.isLit = tag.getBoolean("IsLit");
        this.lastTickGameTime = tag.getLong("LastTickGameTime");
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }
}
