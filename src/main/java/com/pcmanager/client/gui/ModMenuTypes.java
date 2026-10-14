package com.pcmanager.client.gui;

import com.pcmanager.PCManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PCManager.MOD_ID);

    public static final Supplier<MenuType<CremationFurnaceMenu>> CREMATION_FURNACE =
            MENUS.register("cremation_furnace", () -> new MenuType<>(CremationFurnaceMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<TombWorkbenchMenu>> TOMB_WORKBENCH =
            MENUS.register("tomb_workbench", () -> new MenuType<>(TombWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
