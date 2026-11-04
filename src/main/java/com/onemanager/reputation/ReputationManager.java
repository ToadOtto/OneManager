package com.onemanager.reputation;

import com.onemanager.data.CharacterWorldData;

import java.util.List;
import java.util.UUID;

/**
 * Shared (client + server) Knyaz reputation rules: bounds, drift, trend calculation.
 * Contains no client-only or server-only imports.
 */
public final class ReputationManager {

    public static final int MIN_REPUTATION = -100;
    public static final int MAX_REPUTATION = 100;

    /** Reputation drifts towards 0 by this amount every in-game day (20 min). */
    public static final int DRIFT_PER_DAY = 1;

    /** Trend window: last 3 in-game days. */
    public static final long TREND_DAY_TICKS = CharacterWorldData.TAX_CYCLE_TICKS;
    public static final int TREND_WINDOW_DAYS = 3;
    public static final long TREND_WINDOW_TICKS = TREND_DAY_TICKS * TREND_WINDOW_DAYS;
    /** Weights of changes made during day 1 (most recent), day 2 and day 3. */
    public static final double[] TREND_DAY_WEIGHTS = {1.0, 0.5, 0.25};

    /** |score| below this value -> stable ("="). */
    public static final double TREND_WEAK_THRESHOLD = 3.0;
    /** |score| at or above this value -> strong trend (two arrows). */
    public static final double TREND_STRONG_THRESHOLD = 10.0;

    private ReputationManager() {}

    public static int clamp(int value) {
        return Math.max(MIN_REPUTATION, Math.min(MAX_REPUTATION, value));
    }

    /**
     * Weighted sum of reputation changes over the trend window. Fresh changes weigh more.
     */
    public static double computeTrendScore(List<ReputationChange> history, long now) {
        if (history == null || history.isEmpty()) return 0.0;
        double score = 0.0;
        for (ReputationChange change : history) {
            long age = now - change.tick();
            if (age < 0) age = 0; // calendar was rewound by admin, treat as fresh
            if (age >= TREND_WINDOW_TICKS) continue;
            int dayIndex = (int) (age / TREND_DAY_TICKS);
            double weight = TREND_DAY_WEIGHTS[Math.min(dayIndex, TREND_DAY_WEIGHTS.length - 1)];
            score += change.delta() * weight;
        }
        return score;
    }

    /**
     * Trend level: -2 (strong negative), -1 (weak negative), 0 (stable), +1 (weak positive), +2 (strong positive).
     */
    public static int computeTrendLevel(List<ReputationChange> history, long now) {
        double score = computeTrendScore(history, now);
        double abs = Math.abs(score);
        if (abs < TREND_WEAK_THRESHOLD) return 0;
        int level = abs >= TREND_STRONG_THRESHOLD ? 2 : 1;
        return score > 0 ? level : -level;
    }

    /**
     * Faction influence of the player's faction in percent (0..100).
     * STUB: the faction influence system is not implemented yet, always returns 0.
     */
    public static int getFactionInfluencePercent(CharacterWorldData worldData, UUID playerUuid) {
        return 0;
    }

    /** Remaining cooldown in ticks for the given action, 0 when available. */
    public static long getCooldownRemaining(long lastUsedTick, ReputationAction action, long now) {
        if (lastUsedTick < 0 || action == null) return 0L;
        if (now < lastUsedTick) return 0L; // calendar was rewound by admin
        long elapsed = now - lastUsedTick;
        return Math.max(0L, action.getCooldownTicks() - elapsed);
    }

    public static String formatTicks(long ticks) {
        long totalSecs = Math.max(0L, ticks / 20L);
        long hrs = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        if (hrs > 0) return String.format("%d:%02d:%02d", hrs, mins, secs);
        return String.format("%02d:%02d", mins, secs);
    }
}
