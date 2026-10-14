package com.pcmanager.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents the cremated ashes of a deceased character.
 * Produced by burning remains in the Cremation Furnace with 2 buckets of lava.
 */
public class AshesItem extends Item {
    public static final String KEY_OWNER_UUID = "ownerUUID";
    public static final String KEY_DEAD_CHAR_NAME = "deadCharName";
    public static final String KEY_AGE = "age";
    public static final String KEY_PROFESSIONS = "professions";
    public static final String KEY_LINEAGE = "lineage";
    public static final String KEY_FACTION = "faction";
    public static final String KEY_DEATH_DATE = "deathDate";
    public static final String KEY_DEATH_REASON = "deathReason";
    public static final String KEY_CREMATION_DATE = "cremationDate";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public AshesItem(Properties properties) {
        super(properties);
    }

    public static CompoundTag getCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : null;
    }

    public static String getFaction(CompoundTag tag) {
        if (tag == null) return "Не указана";
        if (tag.contains(KEY_FACTION, Tag.TAG_STRING) && !tag.getString(KEY_FACTION).isEmpty()) {
            return tag.getString(KEY_FACTION);
        }
        if (tag.contains(KEY_LINEAGE, Tag.TAG_STRING) && !tag.getString(KEY_LINEAGE).isEmpty()) {
            return tag.getString(KEY_LINEAGE);
        }
        return "Не указана";
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(KEY_DEAD_CHAR_NAME, Tag.TAG_STRING)) {
            String name = tag.getString(KEY_DEAD_CHAR_NAME).trim();
            if (!name.isEmpty()) {
                return Component.literal("Прах: " + name);
            }
        }
        return Component.literal("Прах");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = getCustomTag(stack);
        if (tag == null) {
            super.appendHoverText(stack, context, tooltip, flag);
            return;
        }

        // Faction
        String faction = getFaction(tag);
        tooltip.add(Component.literal("Фракция: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(faction).withStyle(ChatFormatting.GOLD)));

        // Age
        int age = tag.getInt(KEY_AGE);
        tooltip.add(Component.literal("Возраст: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(age)).withStyle(ChatFormatting.YELLOW)));

        // Professions
        if (tag.contains(KEY_PROFESSIONS, Tag.TAG_LIST)) {
            ListTag profsList = tag.getList(KEY_PROFESSIONS, Tag.TAG_STRING);
            List<String> profs = new ArrayList<>();
            for (int i = 0; i < profsList.size(); i++) {
                profs.add(profsList.getString(i));
            }
            String profsString = profs.isEmpty() ? "Нет профессий" : String.join(", ", profs);
            tooltip.add(Component.literal("Профессии: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(profsString).withStyle(ChatFormatting.GREEN)));
        }

        // Cause of death
        String reason = tag.getString(KEY_DEATH_REASON);
        if (!reason.isEmpty()) {
            tooltip.add(Component.literal("Причина смерти: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(reason).withStyle(ChatFormatting.WHITE)));
        }

        // Death Date
        long deathDate = tag.getLong(KEY_DEATH_DATE);
        if (deathDate > 0) {
            String dateFormatted = DATE_FORMATTER.format(Instant.ofEpochMilli(deathDate));
            tooltip.add(Component.literal("Дата гибели: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(dateFormatted).withStyle(ChatFormatting.DARK_GRAY)));
        }

        tooltip.add(Component.literal("Останки преданы огню. Прах упокоен в вечности...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    public static ItemStack createAshes(ItemStack remains) {
        ItemStack ashes = new ItemStack(ModItems.ASHES.get());
        CompoundTag tag = RemainsItem.getCustomTag(remains);
        CompoundTag ashesTag = tag != null ? tag.copy() : new CompoundTag();
        ashesTag.putLong(KEY_CREMATION_DATE, System.currentTimeMillis());

        CustomData.set(DataComponents.CUSTOM_DATA, ashes, ashesTag);

        String name = ashesTag.getString(KEY_DEAD_CHAR_NAME);
        if (name.isEmpty()) {
            name = "Безымянный";
        }
        ashes.set(DataComponents.CUSTOM_NAME, Component.literal("Прах: " + name));
        return ashes;
    }

    public static boolean isAshes(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.ASHES.get());
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.hasUUID(KEY_OWNER_UUID)) {
            return tag.getUUID(KEY_OWNER_UUID);
        }
        return null;
    }

    public static String getCharacterName(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(KEY_DEAD_CHAR_NAME, Tag.TAG_STRING)) {
            return tag.getString(KEY_DEAD_CHAR_NAME);
        }
        return "Неизвестный";
    }

    public static long getDeathDate(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(KEY_DEATH_DATE)) {
            return tag.getLong(KEY_DEATH_DATE);
        }
        return 0;
    }
}
