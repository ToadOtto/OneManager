package com.onemanager.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class LockpickItem extends Item {

    private final int durationTicks;
    private final String tooltipKey;
    private final int qteIntervalMin;
    private final int qteIntervalMax;
    private final int qteWindowTicks;
    private final int maxMistakes;

    public LockpickItem(Properties properties, int durationTicks, String tooltipKey,
                        int qteIntervalMin, int qteIntervalMax, int qteWindowTicks, int maxMistakes) {
        super(properties);
        this.durationTicks = durationTicks;
        this.tooltipKey = tooltipKey;
        this.qteIntervalMin = qteIntervalMin;
        this.qteIntervalMax = qteIntervalMax;
        this.qteWindowTicks = qteWindowTicks;
        this.maxMistakes = maxMistakes;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getDurationSeconds() {
        return durationTicks / 20;
    }

    public int getQteIntervalMin() {
        return qteIntervalMin;
    }

    public int getQteIntervalMax() {
        return qteIntervalMax;
    }

    public int getQteWindowTicks() {
        return qteWindowTicks;
    }

    public int getMaxMistakes() {
        return maxMistakes;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        if (tooltipKey != null && !tooltipKey.isEmpty()) {
            tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.literal("Время взлома: " + getDurationSeconds() + " сек.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
