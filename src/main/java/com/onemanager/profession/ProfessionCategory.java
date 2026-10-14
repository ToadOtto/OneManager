package com.onemanager.profession;

public enum ProfessionCategory {
    STATE("Государственная должность", 1, 0xFFE53935),
    MAIN("Основная", 2, 0xFF4CAF50),
    SECONDARY("Дополнительная", 1, 0xFFFFA500);

    private final String displayName;
    private final int maxOccupants;
    private final int color;

    ProfessionCategory(String displayName, int maxOccupants, int color) {
        this.displayName = displayName;
        this.maxOccupants = maxOccupants;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxOccupants() {
        return maxOccupants;
    }

    public int getColor() {
        return color;
    }
}
