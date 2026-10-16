package com.onemanager.block.entity;

import com.onemanager.block.TreasuryChestBlock;
import com.onemanager.client.gui.TreasuryChestMenu;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.TreasuryChestEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TreasuryChestBlockEntity extends BlockEntity implements MenuProvider {
    public static final String KEY_STORED_AMOUNT = "StoredAmount";

    private int storedAmount = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> TreasuryChestBlockEntity.this.storedAmount;
                case 1 -> TreasuryChestBlockEntity.this.getMaxCapacity();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                TreasuryChestBlockEntity.this.storedAmount = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public TreasuryChestBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.TREASURY_CHEST.get(), pos, blockState);
    }

    public boolean isLarge() {
        if (getBlockState().getBlock() instanceof TreasuryChestBlock chestBlock) {
            return chestBlock.isLarge();
        }
        return false;
    }

    public int getMaxCapacity() {
        return isLarge() ? TreasuryChestEntry.LARGE_CAPACITY : TreasuryChestEntry.SMALL_CAPACITY;
    }

    public int getStoredAmount() {
        if (this.level instanceof ServerLevel serverLevel) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            String dim = serverLevel.dimension().location().toString();
            TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, this.worldPosition);
            if (entry != null && entry.getAmount() != this.storedAmount) {
                this.storedAmount = (int) entry.getAmount();
                setChanged();
            }
        }
        return storedAmount;
    }

    public void setStoredAmountFromWorldData(int amount) {
        this.storedAmount = Math.max(0, Math.min(getMaxCapacity(), amount));
        setChanged();
    }

    public void setStoredAmountAndSync(int amount) {
        this.storedAmount = Math.max(0, Math.min(getMaxCapacity(), amount));
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            String dim = serverLevel.dimension().location().toString();
            worldData.updateTreasuryChestAmount(dim, this.worldPosition, this.storedAmount);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            String dim = serverLevel.dimension().location().toString();
            TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, this.worldPosition);
            if (entry != null) {
                this.storedAmount = (int) entry.getAmount();
            }
        }
    }

    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_STORED_AMOUNT, this.storedAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_STORED_AMOUNT)) {
            this.storedAmount = Math.max(0, tag.getInt(KEY_STORED_AMOUNT));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(isLarge() ? "container.onemanager.large_treasury_chest" : "container.onemanager.small_treasury_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TreasuryChestMenu(containerId, playerInventory, this, this.dataAccess);
    }
}
