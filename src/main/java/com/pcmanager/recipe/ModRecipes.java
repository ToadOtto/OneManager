package com.pcmanager.recipe;

import com.pcmanager.PCManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PCManager.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<NoteCopyRecipe>> NOTE_COPY_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_note_copy", () -> new SimpleCraftingRecipeSerializer<>(NoteCopyRecipe::new));
}
