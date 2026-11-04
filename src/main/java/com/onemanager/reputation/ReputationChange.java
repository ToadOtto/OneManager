package com.onemanager.reputation;

import net.minecraft.nbt.CompoundTag;

/**
 * Single change of Knyaz reputation, used for trend calculation.
 *
 * @param tick   world calendar tick ({@code CharacterWorldData#getTotalElapsedTicks()}) when the change happened
 * @param delta  signed reputation change actually applied (after clamping)
 * @param source short technical source tag (e.g. "action:2", "drift", "admin")
 */
public record ReputationChange(long tick, int delta, String source) {

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("tick", tick);
        tag.putInt("delta", delta);
        tag.putString("source", source != null ? source : "");
        return tag;
    }

    public static ReputationChange fromNBT(CompoundTag tag) {
        return new ReputationChange(tag.getLong("tick"), tag.getInt("delta"), tag.getString("source"));
    }
}
