package com.pcmanager.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoteItem extends Item {
    public static final String KEY_TITLE = "title";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_SEALED = "sealed";
    public static final String KEY_IS_COPY = "is_copy";
    public static final String KEY_IS_OPENED = "is_opened";
    public static final String KEY_AUTHOR = "author";

    public NoteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static CompoundTag getCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : null;
    }

    public static boolean isSealed(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag != null && tag.getBoolean(KEY_SEALED);
    }

    public static boolean isCopy(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag != null && tag.getBoolean(KEY_IS_COPY);
    }

    public static boolean isOpened(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag != null && tag.getBoolean(KEY_IS_OPENED);
    }

    public static String getAuthor(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(KEY_AUTHOR, Tag.TAG_STRING)) {
            String author = tag.getString(KEY_AUTHOR).trim();
            if (!author.isEmpty()) return author;
        }
        return "Неизвестный автор";
    }

    public static String getTitle(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag != null ? tag.getString(KEY_TITLE) : "";
    }

    public static List<String> getPages(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag == null || !tag.contains(KEY_PAGES, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }
        ListTag list = tag.getList(KEY_PAGES, Tag.TAG_STRING);
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            pages.add(list.getString(i));
        }
        return pages;
    }

    public static void setNoteData(ItemStack stack, String title, List<String> pages, boolean sealed, boolean isCopy, boolean isOpened, String author) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_TITLE, title != null ? title.trim() : "");
            ListTag list = new ListTag();
            if (pages != null) {
                for (String p : pages) {
                    list.add(StringTag.valueOf(p));
                }
            }
            tag.put(KEY_PAGES, list);
            tag.putBoolean(KEY_SEALED, sealed);
            tag.putBoolean(KEY_IS_COPY, isCopy);
            tag.putBoolean(KEY_IS_OPENED, isOpened);
            tag.putString(KEY_AUTHOR, author != null && !author.isBlank() ? author.trim() : "Неизвестный автор");
        });
    }

    public static void setNoteData(ItemStack stack, String title, List<String> pages, boolean sealed, boolean isCopy) {
        setNoteData(stack, title, pages, sealed, isCopy, false, getAuthor(stack));
    }

    public static ItemStack createOpenedLetter(String title, List<String> pages, String author, boolean isCopy) {
        ItemStack letter = new ItemStack(ModItems.NOTE.get());
        setNoteData(letter, title, pages, true, isCopy, true, author);
        return letter;
    }

    public static ItemStack createCopy(String title, List<String> pages, String author, boolean isCopy) {
        ItemStack copy = new ItemStack(ModItems.NOTE.get());
        setNoteData(copy, title, pages, true, isCopy, false, author);
        return copy;
    }

    public static ItemStack createCopy(String title, List<String> pages) {
        return createCopy(title, pages, "Неизвестный автор", true);
    }

    @Override
    public Component getName(ItemStack stack) {
        String title = getTitle(stack).trim();
        if (isOpened(stack)) {
            return title.isEmpty() ? Component.literal("Вскрытое письмо") : Component.literal("Вскрытое письмо: " + title);
        }
        if (isCopy(stack)) {
            return title.isEmpty() ? Component.literal("Копия записки") : Component.literal("Копия записки: " + title);
        }
        if (isSealed(stack)) {
            return title.isEmpty() ? Component.literal("Записка") : Component.literal("Записка: " + title);
        }
        return title.isEmpty() ? Component.literal("Записка (черновик)") : Component.literal("Записка: " + title);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Голубая метка (Оригинал / Копия)
        if (isCopy(stack)) {
            tooltip.add(Component.literal("Копия").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal("Оригинал").withStyle(ChatFormatting.AQUA));
        }

        if (isSealed(stack)) {
            String author = getAuthor(stack);
            tooltip.add(Component.literal("Автор: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(author).withStyle(ChatFormatting.WHITE)));

            if (isOpened(stack)) {
                tooltip.add(Component.literal("Вскрытое письмо (нельзя отправить)").withStyle(ChatFormatting.DARK_RED));
                tooltip.add(Component.literal("ПКМ для чтения").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.literal("ПКМ: отправить персонажу").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal("Shift+ПКМ: прочитать").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.literal("Черновик").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("ПКМ: редактировать").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openClientScreen(hand, stack, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            openClientScreen(context.getHand(), context.getItemInHand(), context.getPlayer());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private void openClientScreen(InteractionHand hand, ItemStack stack, Player player) {
        com.pcmanager.client.gui.note.NoteClientHelper.handleNoteUse(hand, stack, player);
    }
}
