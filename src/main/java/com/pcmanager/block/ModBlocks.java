package com.pcmanager.block;

import com.pcmanager.PCManager;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PCManager.MOD_ID);

    public static final DeferredBlock<TombWorkbenchBlock> TOMB_WORKBENCH = BLOCKS.register("tomb_workbench",
            () -> new TombWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<TombstoneBlock> TOMBSTONE = BLOCKS.register("tombstone",
            () -> new TombstoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5F)
                    .noOcclusion()));

    public static final DeferredBlock<CremationFurnaceBlock> CREMATION_FURNACE = BLOCKS.register("cremation_furnace",
            () -> new CremationFurnaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .lightLevel(state -> state.getValue(CremationFurnaceBlock.LIT) ? 13 : 0)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<UrnBlock> URN = BLOCKS.register("urn",
            () -> new UrnBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.CLAY)
                    .strength(0.5F)
                    .noOcclusion()));
}
