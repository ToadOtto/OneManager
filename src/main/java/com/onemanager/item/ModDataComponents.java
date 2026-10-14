package com.onemanager.item;

import com.mojang.serialization.Codec;
import com.onemanager.OneManager;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OneManager.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> KNYAZ_NAME =
            DATA_COMPONENT_TYPES.register("knyaz_name", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final DeferredRegister<DataComponentType<?>> LEGACY_DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "monarchcrownmod");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LEGACY_KNYAZ_NAME =
            LEGACY_DATA_COMPONENT_TYPES.register("knyaz_name", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());
}
