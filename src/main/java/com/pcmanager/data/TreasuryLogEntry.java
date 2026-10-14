package com.pcmanager.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Record representing a single treasury transaction entry.
 */
public record TreasuryLogEntry(
        String characterName,
        String playerName,
        String action,
        String timestamp,
        long treasuryAfter
) {
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("char", characterName != null ? characterName : "");
        tag.putString("player", playerName != null ? playerName : "");
        tag.putString("action", action != null ? action : "");
        tag.putString("time", timestamp != null ? timestamp : "");
        tag.putLong("after", treasuryAfter);
        return tag;
    }

    public static TreasuryLogEntry fromNBT(CompoundTag tag) {
        return new TreasuryLogEntry(
                tag.getString("char"),
                tag.getString("player"),
                tag.getString("action"),
                tag.getString("time"),
                tag.getLong("after")
        );
    }

    public String toLogFileLine() {
        return "[" + timestamp + "] " + getDisplayName() + " | " + action + " | Состояние казны: " + String.format("%,d", treasuryAfter).replace(',', ' ') + " $PR";
    }

    public String getDisplayName() {
        if (characterName != null && !characterName.trim().isEmpty() && !characterName.equalsIgnoreCase(playerName)) {
            return characterName + " (" + playerName + ")";
        }
        return playerName != null && !playerName.isEmpty() ? playerName : "Неизвестно";
    }
}
