package com.zhabaotto.monarchcrownmod.init;

import net.minecraft.world.item.ArmorMaterial;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            com.onemanager.item.ModArmorMaterials.LEGACY_ARMOR_MATERIALS;

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MONARCH_CROWN_MATERIAL =
            com.onemanager.item.ModArmorMaterials.LEGACY_MONARCH_CROWN_MATERIAL;
}
