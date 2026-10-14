package com.pcmanager.entity;

import com.pcmanager.PCManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PCManager.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MemorialSignEntity>> MEMORIAL_SIGN =
            ENTITIES.register("memorial_sign", () -> EntityType.Builder.<MemorialSignEntity>of(
                            MemorialSignEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("memorial_sign"));
}
