package com.onemanager.block.entity;

import com.onemanager.item.RemainsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TombstoneBlockEntity extends BlockEntity {
    private static final String KEY_STORED_REMAINS = "storedRemains";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private ItemStack storedRemains = ItemStack.EMPTY;

    public TombstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOMBSTONE.get(), pos, state);
    }

    public boolean isOccupied() {
        return !storedRemains.isEmpty();
    }

    public ItemStack getStoredRemains() {
        return storedRemains.copy();
    }

    public void setStoredRemains(ItemStack stack) {
        this.storedRemains = stack != null ? stack.copy() : ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getOwnerUUID() {
        CompoundTag tag = RemainsItem.getCustomTag(storedRemains);
        if (tag != null && tag.hasUUID(RemainsItem.KEY_OWNER_UUID)) {
            return tag.getUUID(RemainsItem.KEY_OWNER_UUID);
        }
        return null;
    }

    public String getDeadCharName() {
        CompoundTag tag = RemainsItem.getCustomTag(storedRemains);
        if (tag != null && tag.contains(RemainsItem.KEY_DEAD_CHAR_NAME, Tag.TAG_STRING)) {
            return tag.getString(RemainsItem.KEY_DEAD_CHAR_NAME);
        }
        return "Неизвестный";
    }

    public String getEpitaph() {
        if (!isOccupied()) {
            return "Могила пуста";
        }

        CompoundTag tag = RemainsItem.getCustomTag(storedRemains);
        if (tag == null) {
            return "Здесь покоится: Неизвестный";
        }

        String name = tag.getString(RemainsItem.KEY_DEAD_CHAR_NAME);
        if (name.isEmpty()) name = "Безымянный";

        int age = tag.getInt(RemainsItem.KEY_AGE);

        String faction = RemainsItem.getFaction(tag);

        String cause = tag.getString(RemainsItem.KEY_DEATH_REASON);
        if (cause.isEmpty()) cause = "Неизвестная причина";

        long deathDate = tag.getLong(RemainsItem.KEY_DEATH_DATE);
        String dateFormatted = deathDate > 0 ? DATE_FORMATTER.format(Instant.ofEpochMilli(deathDate)) : "Давным-давно";

        List<String> profs = new ArrayList<>();
        if (tag.contains(RemainsItem.KEY_PROFESSIONS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(RemainsItem.KEY_PROFESSIONS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                profs.add(list.getString(i));
            }
        }
        String profsString = profs.isEmpty() ? "Без профессии" : String.join(", ", profs);

        return "Здесь покоится: " + name + ", " + age + " лет, Профессия: [" + profsString + "], Фракция: " + faction + ", Умер: " + dateFormatted + ", Причина: " + cause;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!storedRemains.isEmpty()) {
            tag.put(KEY_STORED_REMAINS, storedRemains.save(registries, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_STORED_REMAINS, Tag.TAG_COMPOUND)) {
            this.storedRemains = ItemStack.parseOptional(registries, tag.getCompound(KEY_STORED_REMAINS));
        } else {
            this.storedRemains = ItemStack.EMPTY;
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
