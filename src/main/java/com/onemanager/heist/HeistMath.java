package com.onemanager.heist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

public final class HeistMath {
    private HeistMath() {}

    public record RiskInfo(String name, ChatFormatting color, double noisePercent) {
        public MutableComponent formatted() {
            return Component.literal(String.format("%.1f%% (%s)", noisePercent, name)).withStyle(color);
        }
    }

    public static double calculateNoise(long stolenAmount, long totalTreasury) {
        if (stolenAmount <= 0) return 0.0;
        if (totalTreasury <= 0) return 100.0;
        double noise = ((double) stolenAmount / (double) totalTreasury) * 100.0;
        return Math.min(100.0, Math.max(0.0, noise));
    }

    public static RiskInfo getRiskInfo(double noisePercent) {
        if (noisePercent < 15.0) {
            return new RiskInfo("Тихо", ChatFormatting.GREEN, noisePercent);
        } else if (noisePercent < 40.0) {
            return new RiskInfo("Шорох", ChatFormatting.YELLOW, noisePercent);
        } else if (noisePercent < 70.0) {
            return new RiskInfo("Грохот", ChatFormatting.GOLD, noisePercent);
        } else {
            return new RiskInfo("Тревога близко", ChatFormatting.DARK_RED, noisePercent);
        }
    }

    public static String formatTimer(int ticksRemaining) {
        int totalSeconds = Math.max(0, ticksRemaining / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static int nextQteDelay(RandomSource random, int minTicks, int maxTicks) {
        if (maxTicks <= minTicks) return minTicks;
        return minTicks + random.nextInt(maxTicks - minTicks + 1);
    }
}
