package com.onemanager.block.entity;

import com.onemanager.OneManager;
import com.onemanager.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OneManager.MOD_ID);

    public static final Supplier<BlockEntityType<TombstoneBlockEntity>> TOMBSTONE =
            BLOCK_ENTITIES.register("tombstone",
                    () -> BlockEntityType.Builder.of(TombstoneBlockEntity::new, ModBlocks.TOMBSTONE.get()).build(null));

    public static final Supplier<BlockEntityType<CremationFurnaceBlockEntity>> CREMATION_FURNACE =
            BLOCK_ENTITIES.register("cremation_furnace",
                    () -> BlockEntityType.Builder.of(CremationFurnaceBlockEntity::new, ModBlocks.CREMATION_FURNACE.get()).build(null));

    public static final Supplier<BlockEntityType<UrnBlockEntity>> URN =
            BLOCK_ENTITIES.register("urn",
                    () -> BlockEntityType.Builder.of(UrnBlockEntity::new, ModBlocks.URN.get()).build(null));

    public static final Supplier<BlockEntityType<TreasuryChestBlockEntity>> TREASURY_CHEST =
            BLOCK_ENTITIES.register("treasury_chest",
                    () -> BlockEntityType.Builder.of(TreasuryChestBlockEntity::new,
                            ModBlocks.SMALL_TREASURY_CHEST.get(),
                            ModBlocks.LARGE_TREASURY_CHEST.get()).build(null));
}
