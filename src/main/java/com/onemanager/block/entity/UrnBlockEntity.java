package com.onemanager.block.entity;

import com.onemanager.item.AshesItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class UrnBlockEntity extends BlockEntity {
    private static final String KEY_STORED_ASHES = "storedAshes";

    private ItemStack storedAshes = ItemStack.EMPTY;

    public UrnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.URN.get(), pos, state);
    }

    public boolean hasAshes() {
        return !storedAshes.isEmpty();
    }

    public ItemStack getStoredAshes() {
        return storedAshes.copy();
    }

    public void setStoredAshes(ItemStack stack) {
        this.storedAshes = stack != null ? stack.copy() : ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getOwnerUUID() {
        return AshesItem.getOwnerUUID(storedAshes);
    }

    public String getAshesName() {
        if (!hasAshes()) {
            return "Пусто";
        }
        return AshesItem.getCharacterName(storedAshes);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!storedAshes.isEmpty()) {
            tag.put(KEY_STORED_ASHES, storedAshes.save(registries, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_STORED_ASHES, Tag.TAG_COMPOUND)) {
            this.storedAshes = ItemStack.parseOptional(registries, tag.getCompound(KEY_STORED_ASHES));
        } else {
            this.storedAshes = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
