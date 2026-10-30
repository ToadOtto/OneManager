package com.onemanager.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class TreasuryChestEntry {
    public static final int SMALL_CAPACITY = 500;
    public static final int LARGE_CAPACITY = 1250;
    public static final int SMALL_COST = 250;
    public static final int LARGE_COST = 500;

    private final UUID id;
    private final String dimension;
    private final BlockPos pos;
    private final boolean large;
    private long amount;
    private long pendingStolen = 0L;
    private boolean sealBroken = false;
    private final String placedDate;
    private final String placedBy;

    public TreasuryChestEntry(UUID id, String dimension, BlockPos pos, boolean large, long amount, String placedDate, String placedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.dimension = dimension != null && !dimension.isEmpty() ? dimension : "minecraft:overworld";
        this.pos = pos != null ? pos.immutable() : BlockPos.ZERO;
        this.large = large;
        this.placedDate = placedDate != null ? placedDate : "";
        this.placedBy = placedBy != null ? placedBy : "";
        setAmount(amount);
    }

    public UUID getId() {
        return id;
    }

    public String getDimension() {
        return dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isLarge() {
        return large;
    }

    public int getCapacity() {
        return large ? LARGE_CAPACITY : SMALL_CAPACITY;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0L, Math.min(getCapacity(), amount));
    }

    public long getPendingStolen() {
        return pendingStolen;
    }

    public void setPendingStolen(long pendingStolen) {
        this.pendingStolen = Math.max(0L, pendingStolen);
    }

    public boolean isSealBroken() {
        return sealBroken;
    }

    public void setSealBroken(boolean sealBroken) {
        this.sealBroken = sealBroken;
    }

    public boolean isInactive() {
        return sealBroken;
    }

    public boolean isLooted() {
        return pendingStolen > 0 || sealBroken;
    }

    public long getEffectiveAmount() {
        return Math.max(0L, amount - pendingStolen);
    }

    public long getFreeSpace() {
        return Math.max(0L, getCapacity() - amount);
    }

    public String getPlacedDate() {
        return placedDate;
    }

    public String getPlacedBy() {
        return placedBy;
    }

    public String getTypeTitle() {
        return large ? "Большой Сундук Казны" : "Малый Сундук Казны";
    }

    public String getFormattedCoords() {
        return String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean matchesLocation(String dim, BlockPos blockPos) {
        if (blockPos == null || !this.pos.equals(blockPos)) return false;
        if (dim == null || dim.isEmpty()) return true;
        return this.dimension.equals(dim);
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("dimension", dimension);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putBoolean("large", large);
        tag.putLong("amount", amount);
        tag.putLong("pendingStolen", pendingStolen);
        tag.putBoolean("sealBroken", sealBroken);
        tag.putString("placedDate", placedDate);
        tag.putString("placedBy", placedBy);
        return tag;
    }

    public static TreasuryChestEntry fromNBT(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        String dim = tag.contains("dimension") ? tag.getString("dimension") : "minecraft:overworld";
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        boolean large = tag.getBoolean("large");
        long amount = tag.getLong("amount");
        String placedDate = tag.getString("placedDate");
        String placedBy = tag.getString("placedBy");
        TreasuryChestEntry entry = new TreasuryChestEntry(id, dim, pos, large, amount, placedDate, placedBy);
        if (tag.contains("pendingStolen")) {
            entry.setPendingStolen(tag.getLong("pendingStolen"));
        }
        if (tag.contains("sealBroken")) {
            entry.setSealBroken(tag.getBoolean("sealBroken"));
        }
        return entry;
    }

    public String toFileLine(int number) {
        return String.format("#%d | %s | Координаты: [%d, %d, %d] (%s) | Заполнено: %d / %d $PR | Установлен: %s (%s)",
                number, getTypeTitle(), pos.getX(), pos.getY(), pos.getZ(), dimension, amount, getCapacity(), placedDate, placedBy);
    }
}
