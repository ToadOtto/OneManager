package com.onemanager.reputation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of player abilities unlocked by Knyaz reputation (STUBS for now).
 * <p>
 * ID scheme (mirrored): tens digit = tier (1..4), units digit = slot (1..4), sign = side.
 * <ul>
 *     <li>Positive tiers: 11-14 (from +1), 21-24 (from +25), 31-34 (from +50), 41-44 (from +75)</li>
 *     <li>Negative tiers: -11..-14 (from -1), -21..-24 (from -25), -31..-34 (from -50), -41..-44 (from -75)</li>
 * </ul>
 * To edit an ability, simply change its name/description in the list below by ID.
 */
public record ReputationAbility(int id, String name, String description) {

    /** Absolute reputation threshold (inclusive) for tiers 1..4. */
    public static final int[] TIER_THRESHOLDS = {1, 25, 50, 75};

    private static final Map<Integer, ReputationAbility> REGISTRY = new LinkedHashMap<>();

    static {
        // ===== Positive side (green) =====
        reg(11, "Способность 11", "Заглушка. Описание способности будет добавлено позже.");
        reg(12, "Способность 12", "Заглушка. Описание способности будет добавлено позже.");
        reg(13, "Способность 13", "Заглушка. Описание способности будет добавлено позже.");
        reg(14, "Способность 14", "Заглушка. Описание способности будет добавлено позже.");

        reg(21, "Способность 21", "Заглушка. Описание способности будет добавлено позже.");
        reg(22, "Способность 22", "Заглушка. Описание способности будет добавлено позже.");
        reg(23, "Способность 23", "Заглушка. Описание способности будет добавлено позже.");
        reg(24, "Способность 24", "Заглушка. Описание способности будет добавлено позже.");

        reg(31, "Способность 31", "Заглушка. Описание способности будет добавлено позже.");
        reg(32, "Способность 32", "Заглушка. Описание способности будет добавлено позже.");
        reg(33, "Способность 33", "Заглушка. Описание способности будет добавлено позже.");
        reg(34, "Способность 34", "Заглушка. Описание способности будет добавлено позже.");

        reg(41, "Способность 41", "Заглушка. Описание способности будет добавлено позже.");
        reg(42, "Способность 42", "Заглушка. Описание способности будет добавлено позже.");
        reg(43, "Способность 43", "Заглушка. Описание способности будет добавлено позже.");
        reg(44, "Способность 44", "Заглушка. Описание способности будет добавлено позже.");

        // ===== Negative side (red) =====
        reg(-11, "Способность -11", "Заглушка. Описание способности будет добавлено позже.");
        reg(-12, "Способность -12", "Заглушка. Описание способности будет добавлено позже.");
        reg(-13, "Способность -13", "Заглушка. Описание способности будет добавлено позже.");
        reg(-14, "Способность -14", "Заглушка. Описание способности будет добавлено позже.");

        reg(-21, "Способность -21", "Заглушка. Описание способности будет добавлено позже.");
        reg(-22, "Способность -22", "Заглушка. Описание способности будет добавлено позже.");
        reg(-23, "Способность -23", "Заглушка. Описание способности будет добавлено позже.");
        reg(-24, "Способность -24", "Заглушка. Описание способности будет добавлено позже.");

        reg(-31, "Способность -31", "Заглушка. Описание способности будет добавлено позже.");
        reg(-32, "Способность -32", "Заглушка. Описание способности будет добавлено позже.");
        reg(-33, "Способность -33", "Заглушка. Описание способности будет добавлено позже.");
        reg(-34, "Способность -34", "Заглушка. Описание способности будет добавлено позже.");

        reg(-41, "Способность -41", "Заглушка. Описание способности будет добавлено позже.");
        reg(-42, "Способность -42", "Заглушка. Описание способности будет добавлено позже.");
        reg(-43, "Способность -43", "Заглушка. Описание способности будет добавлено позже.");
        reg(-44, "Способность -44", "Заглушка. Описание способности будет добавлено позже.");
    }

    private static void reg(int id, String name, String description) {
        REGISTRY.put(id, new ReputationAbility(id, name, description));
    }

    /** Tier 1..4 derived from the ID. */
    public int tier() {
        return Math.abs(id) / 10;
    }

    public boolean isPositive() {
        return id > 0;
    }

    /** Signed inclusive threshold, e.g. +25 or -50. */
    public int threshold() {
        return signedThreshold(tier(), isPositive());
    }

    public boolean isUnlocked(int reputation) {
        return isTierUnlocked(tier(), isPositive(), reputation);
    }

    public static int signedThreshold(int tier, boolean positive) {
        int abs = TIER_THRESHOLDS[Math.max(0, Math.min(TIER_THRESHOLDS.length - 1, tier - 1))];
        return positive ? abs : -abs;
    }

    public static boolean isTierUnlocked(int tier, boolean positive, int reputation) {
        int t = signedThreshold(tier, positive);
        return positive ? reputation >= t : reputation <= t;
    }

    public static ReputationAbility byId(int id) {
        return REGISTRY.get(id);
    }

    /** Returns the 4 abilities of the given tier/side, ordered by slot. */
    public static List<ReputationAbility> getTier(int tier, boolean positive) {
        List<ReputationAbility> list = new ArrayList<>();
        for (int slot = 1; slot <= 4; slot++) {
            int id = (tier * 10 + slot) * (positive ? 1 : -1);
            ReputationAbility a = REGISTRY.get(id);
            list.add(a != null ? a : new ReputationAbility(id, "Способность " + id, "Заглушка."));
        }
        return list;
    }

    public static Map<Integer, ReputationAbility> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
