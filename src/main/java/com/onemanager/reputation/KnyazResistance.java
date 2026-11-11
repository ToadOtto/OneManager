package com.onemanager.reputation;

/**
 * Defines Knyaz PvP damage resistance tiers based on current Knyaz reputation (-100 .. +100).
 * <p>
 * Dark Knyaz (negative reputation) gains higher damage reduction against citizen attacks (up to 90%),
 * while Light Knyaz (positive reputation) lowers personal defense in favor of openness and buffs (down to 0%).
 */
public enum KnyazResistance {
    CROWN_PATRONAGE(
            0,
            "Покровительство Короны",
            "Базовая неприкосновенность монарха, дарованная самой Короной и законом государства.",
            60,
            0xFFFFD54F
    ),
    SHIELD_OF_RESISTANCE(
            1,
            "Щит Сопротивления",
            "Недоверие граждан вынуждает правителя держать оборону и быть готовым к удару.",
            60,
            0xFFEF9A9A
    ),
    STONE_OF_SIN(
            2,
            "Камень Греха",
            "Тяжесть суровых решений укрепляет личную гвардию и защиту монарха от покушений.",
            70,
            0xFFE57373
    ),
    FACE_OF_DECAY(
            3,
            "Лик Упадка",
            "Жестокий нрав и подавление инакомыслия окружают Князя мрачной бронёй тирании.",
            80,
            0xFFFF5252
    ),
    TYRANT_SKULL(
            4,
            "Череп Тирана",
            "Абсолютный деспотизм и страх сковали волю подданных, делая правителя почти неуязвимым для бунтовщиков.",
            90,
            0xFFFF1744
    ),
    SHIELD_OF_INSPIRATION(
            5,
            "Щит Вдохновения",
            "Народное уважение поддерживает авторитет и базовую защиту правителя.",
            60,
            0xFFA5D6A7
    ),
    SIGN_OF_HOPE(
            6,
            "Знак Надежды",
            "Доверие к горожанам заставляет Князя ослабить личную охрану ради открытости.",
            50,
            0xFF81C784
    ),
    IDOL_OF_FAITH(
            7,
            "Идол Веры",
            "Праведный правитель ходит среди народа почти без защиты, полагаясь на любовь подданных.",
            25,
            0xFF4CAF50
    ),
    ICON_OF_LIGHT(
            8,
            "Икона Света",
            "Высшая благодетель и полное единение с народом — Князь полностью открыт и отказывается от защиты против граждан.",
            0,
            0xFF00E676
    );

    private final int spriteIndex;
    private final String title;
    private final String description;
    private final int resistancePercent;
    private final int titleColor;

    KnyazResistance(int spriteIndex, String title, String description, int resistancePercent, int titleColor) {
        this.spriteIndex = spriteIndex;
        this.title = title;
        this.description = description;
        this.resistancePercent = resistancePercent;
        this.titleColor = titleColor;
    }

    public int getSpriteIndex() {
        return spriteIndex;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getResistancePercent() {
        return resistancePercent;
    }

    public int getTitleColor() {
        return titleColor;
    }

    /**
     * Multiplier applied to incoming PvP damage from other players: 1.0 - (resistancePercent / 100.0).
     */
    public float getDamageMultiplier() {
        return Math.max(0.0f, Math.min(1.0f, 1.0f - (resistancePercent / 100.0f)));
    }

    /**
     * Resolves the active resistance tier for the given Knyaz reputation (-100 .. +100).
     * Thresholds are inclusive (<= -25, -50, -75, -90 and >= +25, +50, +75, +90).
     */
    public static KnyazResistance fromReputation(int reputation) {
        if (reputation <= -90) {
            return TYRANT_SKULL;
        }
        if (reputation <= -75) {
            return FACE_OF_DECAY;
        }
        if (reputation <= -50) {
            return STONE_OF_SIN;
        }
        if (reputation <= -25) {
            return SHIELD_OF_RESISTANCE;
        }
        if (reputation >= 90) {
            return ICON_OF_LIGHT;
        }
        if (reputation >= 75) {
            return IDOL_OF_FAITH;
        }
        if (reputation >= 50) {
            return SIGN_OF_HOPE;
        }
        if (reputation >= 25) {
            return SHIELD_OF_INSPIRATION;
        }
        return CROWN_PATRONAGE;
    }
}
