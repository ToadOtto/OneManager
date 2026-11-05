package com.onemanager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class HeistSession {

    public static final int DEFAULT_ESCAPE_TICKS = 3600; // 180 seconds (3 minutes)

    private final UUID thiefUuid;
    private long stolenAccumulated;
    private int ticksRemaining;
    private final List<UUID> lootedChestIds = new ArrayList<>();

    public HeistSession(UUID thiefUuid) {
        this.thiefUuid = thiefUuid;
        this.stolenAccumulated = 0L;
        this.ticksRemaining = DEFAULT_ESCAPE_TICKS;
    }

    public HeistSession(UUID thiefUuid, long stolenAccumulated, int ticksRemaining, List<UUID> lootedChestIds) {
        this.thiefUuid = thiefUuid;
        this.stolenAccumulated = stolenAccumulated;
        this.ticksRemaining = ticksRemaining;
        if (lootedChestIds != null) {
            this.lootedChestIds.addAll(lootedChestIds);
        }
    }

    public UUID getThiefUuid() {
        return thiefUuid;
    }

    public long getStolenAccumulated() {
        return stolenAccumulated;
    }

    public void addStolen(long amount) {
        this.stolenAccumulated += Math.max(0L, amount);
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public void resetTimer() {
        this.ticksRemaining = DEFAULT_ESCAPE_TICKS;
    }

    public boolean tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
        return ticksRemaining <= 0;
    }

    public List<UUID> getLootedChestIds() {
        return Collections.unmodifiableList(lootedChestIds);
    }

    public void addLootedChestId(UUID id) {
        if (id != null && !lootedChestIds.contains(id)) {
            lootedChestIds.add(id);
        }
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("thiefUuid", thiefUuid);
        tag.putLong("stolenAccumulated", stolenAccumulated);
        tag.putInt("ticksRemaining", ticksRemaining);
        ListTag list = new ListTag();
        for (UUID id : lootedChestIds) {
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID("id", id);
            list.add(idTag);
        }
        tag.put("lootedChestIds", list);
        return tag;
    }

    public static HeistSession fromNBT(CompoundTag tag) {
        UUID thiefUuid = tag.hasUUID("thiefUuid") ? tag.getUUID("thiefUuid") : UUID.randomUUID();
        long stolen = tag.getLong("stolenAccumulated");
        int ticks = tag.contains("ticksRemaining") ? tag.getInt("ticksRemaining") : DEFAULT_ESCAPE_TICKS;
        List<UUID> chestIds = new ArrayList<>();
        if (tag.contains("lootedChestIds", Tag.TAG_LIST)) {
            ListTag list = tag.getList("lootedChestIds", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag idTag = list.getCompound(i);
                if (idTag.hasUUID("id")) {
                    chestIds.add(idTag.getUUID("id"));
                }
            }
        }
        return new HeistSession(thiefUuid, stolen, ticks, chestIds);
    }
}
