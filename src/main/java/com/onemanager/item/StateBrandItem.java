package com.onemanager.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Гос. Марка - специальная валюта OneHouse3.
 * Выдаётся за выполнение достижений профессий.
 */
public class StateBrandItem extends Item {

    public StateBrandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Специальная государственная валюта.").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Выдаётся за выполнение достижений профессий.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
