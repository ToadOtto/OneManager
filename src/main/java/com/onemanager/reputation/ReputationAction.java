package com.onemanager.reputation;

import com.onemanager.data.CharacterWorldData;

/**
 * Registry of player actions that influence Knyaz reputation.
 * <p>
 * Each entry describes a pair of mirrored actions (+N / -N). The positive and negative variants
 * share a single cooldown per player. All tunable numbers and texts live here.
 */
public enum ReputationAction {
    SUPPORT(1, 1, 1, 0, 0, 0,
            "Поддержать",
            "Выразить недоверие",
            "Публично выразить поддержку Князю и его решениям.",
            "Публично выразить недоверие Князю и его решениям."),

    RUMORS(2, 2, 3, 250, 0, 0,
            "Пустить добрые вести",
            "Распустить грязные сплетни",
            "Распространить по поселению добрые вести о деяниях Князя.",
            "Распустить по поселению грязные сплетни о Князе."),

    CELEBRATION(3, 3, 3, 0, 1, 0,
            "Организовать чествование",
            "Собрать негодующую толпу",
            "Устроить торжественное чествование правителя.",
            "Собрать на площади толпу недовольных правлением Князя."),

    PARADE(4, 4, 3, 0, 2, 0,
            "Провести парад",
            "Устроить бунт",
            "Провести парад во славу Князя и государства.",
            "Устроить бунт против власти Князя."),

    PROPAGANDA(5, 5, 7, 0, 1, 25,
            "Запустить кампанию по пропаганде",
            "Опубликовать разоблачение",
            "Фракция запускает масштабную кампанию по прославлению Князя.",
            "Фракция публикует громкое разоблачение тёмных дел Князя."),

    REPRESSION(10, 10, 7, 0, 2, 50,
            "Репрессировать недовольных",
            "Поднять восстание",
            "Фракция подавляет недовольных правлением Князя.",
            "Фракция поднимает вооружённое восстание против Князя.");

    private final int id;
    private final int magnitude;
    private final int cooldownDays;
    private final int costSprota;
    private final int costBrands;
    private final int requiredFactionInfluence;
    private final String positiveName;
    private final String negativeName;
    private final String positiveDesc;
    private final String negativeDesc;

    ReputationAction(int id, int magnitude, int cooldownDays, int costSprota, int costBrands, int requiredFactionInfluence,
                     String positiveName, String negativeName, String positiveDesc, String negativeDesc) {
        this.id = id;
        this.magnitude = magnitude;
        this.cooldownDays = cooldownDays;
        this.costSprota = costSprota;
        this.costBrands = costBrands;
        this.requiredFactionInfluence = requiredFactionInfluence;
        this.positiveName = positiveName;
        this.negativeName = negativeName;
        this.positiveDesc = positiveDesc;
        this.negativeDesc = negativeDesc;
    }

    public int getId() { return id; }
    public int getMagnitude() { return magnitude; }
    public int getDelta(boolean positive) { return positive ? magnitude : -magnitude; }
    public int getCooldownDays() { return cooldownDays; }
    public long getCooldownTicks() { return (long) cooldownDays * CharacterWorldData.TAX_CYCLE_TICKS; }
    public int getCostSprota() { return costSprota; }
    public int getCostBrands() { return costBrands; }
    public int getRequiredFactionInfluence() { return requiredFactionInfluence; }
    public boolean isFactionAction() { return requiredFactionInfluence > 0; }
    public String getName(boolean positive) { return positive ? positiveName : negativeName; }
    public String getDescription(boolean positive) { return positive ? positiveDesc : negativeDesc; }

    public String getCooldownText() {
        if (cooldownDays == 1) return "раз в день (20 мин.)";
        return "раз в " + cooldownDays + " дн. (" + (cooldownDays * 20) + " мин.)";
    }

    public String getCostText() {
        if (costSprota > 0) return costSprota + " $PR";
        if (costBrands > 0) return costBrands + " " + (costBrands == 1 ? "Гос. Марка" : "Гос. Марки");
        return "Бесплатно";
    }

    public static ReputationAction byId(int id) {
        for (ReputationAction a : values()) {
            if (a.id == id) return a;
        }
        return null;
    }
}
