package com.onemanager.reputation;

/**
 * Unique reputation events available at extreme Knyaz reputation (STUBS for now).
 */
public enum ReputationEvent {
    ROYAL_HUNT(-90, false,
            "Королевская Охота",
            "Требует согласия большинства игроков. Запускает событие, в котором у Князя на 5 минут "
                    + "отключаются все резисты и способности, а игроки должны убить его за это время. "
                    + "Если Князь выживет, его репутация вырастет на +20."),

    RULER_MONUMENT(90, true,
            "Монумент Правителя",
            "Князь сам решает добровольно совершить это действие. Он умирает и получает статус легенды, "
                    + "а власть переходит наследнику. Поселение получает огромный бафф, деньги, расширение мира и многое другое.");

    private final int threshold;
    private final boolean knyazOnly;
    private final String title;
    private final String description;

    ReputationEvent(int threshold, boolean knyazOnly, String title, String description) {
        this.threshold = threshold;
        this.knyazOnly = knyazOnly;
        this.title = title;
        this.description = description;
    }

    public int getThreshold() { return threshold; }
    public boolean isKnyazOnly() { return knyazOnly; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public boolean isUnlocked(int reputation) {
        return threshold >= 0 ? reputation >= threshold : reputation <= threshold;
    }
}
