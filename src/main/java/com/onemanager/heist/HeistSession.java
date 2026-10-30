package com.onemanager.heist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HeistSession {
    public static final int DEFAULT_BUFFER_TICKS = 3600; // 180 seconds (3 minutes)

    private final UUID thiefUuid;
    private long stolenAccumulated;
    private int ticksRemaining;
    private final Set<UUID> lootedChestIds;

    public HeistSession(UUID thiefUuid) {
        this(thiefUuid, 0L, DEFAULT_BUFFER_TICKS, new HashSet<>());
    }

    public HeistSession(UUID thiefUuid, long stolenAccumulated, int ticksRemaining, Set<UUID> lootedChestIds) {
        this.thiefUuid = thiefUuid != null ? thiefUuid : UUID.randomUUID();
        this.stolenAccumulated = Math.max(0L, stolenAccumulated);
        this.ticksRemaining = ticksRemaining;
        this.lootedChestIds = lootedChestIds != null ? lootedChestIds : new HashSet<>();
    }

    public UUID getThiefUuid() {
        return thiefUuid;
    }

    public long getStolenAccumulated() {
        return stolenAccumulated;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public boolean decrementTicks() {
        if (this.ticksRemaining > 0) {
            this.ticksRemaining--;
        }
        return this.ticksRemaining <= 0;
    }

    public void resetTimer() {
        this.ticksRemaining = DEFAULT_BUFFER_TICKS;
    }

    public Set<UUID> getLootedChestIds() {
        return Collections.unmodifiableSet(lootedChestIds);
    }

    public void addLootedChest(UUID chestId, long amount) {
        if (chestId != null) {
            this.lootedChestIds.add(chestId);
        }
        this.stolenAccumulated += Math.max(0L, amount);
        this.ticksRemaining = DEFAULT_BUFFER_TICKS;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("thiefUuid", thiefUuid);
        tag.putLong("stolenAccumulated", stolenAccumulated);
        tag.putInt("ticksRemaining", ticksRemaining);

        ListTag chestsList = new ListTag();
        for (UUID id : lootedChestIds) {
            CompoundTag c = new CompoundTag();
            c.putUUID("chestId", id);
            chestsList.add(c);
        }
        tag.put("lootedChests", chestsList);
        return tag;
    }

    public static HeistSession fromNBT(CompoundTag tag) {
        UUID thiefUuid = tag.hasUUID("thiefUuid") ? tag.getUUID("thiefUuid") : UUID.randomUUID();
        long stolen = tag.getLong("stolenAccumulated");
        int ticks = tag.contains("ticksRemaining") ? tag.getInt("ticksRemaining") : DEFAULT_BUFFER_TICKS;

        Set<UUID> chestIds = new HashSet<>();
        if (tag.contains("lootedChests", Tag.TAG_LIST)) {
            ListTag list = tag.getList("lootedChests", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                if (c.hasUUID("chestId")) {
                    chestIds.add(c.getUUID("chestId"));
                }
            }
        }
        return new HeistSession(thiefUuid, stolen, ticks, chestIds);
    }
}
