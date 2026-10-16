package com.onemanager.item;

import com.onemanager.OneManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OneManager.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ONEMANAGER_TAB = CREATIVE_MODE_TABS.register("onemanager_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.onemanager"))
                    .icon(() -> new ItemStack(ModItems.REMAINS.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TOMB_WORKBENCH.get());
                        output.accept(ModItems.TOMBSTONE.get());
                        output.accept(ModItems.CREMATION_FURNACE.get());
                        output.accept(ModItems.URN.get());
                        output.accept(ModItems.REMAINS.get());
                        output.accept(ModItems.ASHES.get());
                        output.accept(ModItems.STATE_BRAND.get());
                        output.accept(ModItems.MONARCH_CROWN.get());
                        output.accept(ModItems.NEW_LIFE_STRUCTURE.get());
                        output.accept(ModItems.NOTE.get());
                        output.accept(ModItems.MEMORIAL_SIGN.get());
                        output.accept(ModItems.SMALL_TREASURY_CHEST.get());
                        output.accept(ModItems.LARGE_TREASURY_CHEST.get());
                    })
                    .build()
    );
}
