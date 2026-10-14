package com.onemanager.recipe;

import com.onemanager.item.NoteItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

public class NoteCopyRecipe extends CustomRecipe {
    public NoteCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int paperCount = 0;
        ItemStack sourceNote = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.is(Items.PAPER)) {
                    paperCount++;
                } else if (stack.getItem() instanceof NoteItem && NoteItem.isSealed(stack)) {
                    if (!sourceNote.isEmpty()) {
                        return false;
                    }
                    sourceNote = stack;
                } else {
                    return false;
                }
            }
        }

        return !sourceNote.isEmpty() && paperCount == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack sourceNote = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof NoteItem && NoteItem.isSealed(stack)) {
                sourceNote = stack;
                break;
            }
        }

        if (sourceNote.isEmpty()) {
            return ItemStack.EMPTY;
        }

        String title = NoteItem.getTitle(sourceNote);
        List<String> pages = NoteItem.getPages(sourceNote);
        String author = NoteItem.getAuthor(sourceNote);

        // If source is an opened letter ("Вскрытое письмо") or already a copy:
        // produces sealed note with is_copy = true ("Копия").
        // If source is unopened original:
        // produces copy with is_copy = false ("Оригинал").
        boolean resultIsCopy = NoteItem.isOpened(sourceNote) || NoteItem.isCopy(sourceNote);

        return NoteItem.createCopy(title, pages, author, resultIsCopy);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof NoteItem) {
                remaining.set(i, stack.copyWithCount(1));
                break;
            }
        }

        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.NOTE_COPY_SERIALIZER.get();
    }
}
