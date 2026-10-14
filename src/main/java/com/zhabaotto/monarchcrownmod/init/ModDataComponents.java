package com.zhabaotto.monarchcrownmod.init;

import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            com.pcmanager.item.ModDataComponents.LEGACY_DATA_COMPONENT_TYPES;

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> KNYAZ_NAME =
            com.pcmanager.item.ModDataComponents.LEGACY_KNYAZ_NAME;
}
