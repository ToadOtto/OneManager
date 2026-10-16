package com.onemanager.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Record representing a single treasury transaction entry.
 */
public record TreasuryLogEntry(
        String characterName,
        String playerName,
        String action,
        String timestamp,
        long treasuryAfter,
        long gameTick
) {
    public TreasuryLogEntry(
            String characterName,
            String playerName,
            String action,
            String timestamp,
            long treasuryAfter
    ) {
        this(characterName, playerName, action, timestamp, treasuryAfter, -1L);
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("char", characterName != null ? characterName : "");
        tag.putString("player", playerName != null ? playerName : "");
        tag.putString("action", action != null ? action : "");
        tag.putString("time", timestamp != null ? timestamp : "");
        tag.putLong("after", treasuryAfter);
        tag.putLong("tick", gameTick);
        return tag;
    }

    public static TreasuryLogEntry fromNBT(CompoundTag tag) {
        long tick = tag.contains("tick") ? tag.getLong("tick") : -1L;
        return new TreasuryLogEntry(
                tag.getString("char"),
                tag.getString("player"),
                tag.getString("action"),
                tag.getString("time"),
                tag.getLong("after"),
                tick
        );
    }

    public boolean isExpense() {
        if (action == null) return false;
        String trimmed = action.trim();
        return trimmed.startsWith("-") || trimmed.startsWith("Снос");
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
