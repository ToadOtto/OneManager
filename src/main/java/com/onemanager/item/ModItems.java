package com.onemanager.item;

import com.onemanager.OneManager;
import com.onemanager.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OneManager.MOD_ID);

    public static final DeferredItem<RemainsItem> REMAINS = ITEMS.register("remains",
            () -> new RemainsItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<AshesItem> ASHES = ITEMS.register("ashes",
            () -> new AshesItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> TOMB_WORKBENCH = ITEMS.registerSimpleBlockItem("tomb_workbench", ModBlocks.TOMB_WORKBENCH);

    public static final DeferredItem<BlockItem> TOMBSTONE = ITEMS.registerSimpleBlockItem("tombstone", ModBlocks.TOMBSTONE);

    public static final DeferredItem<BlockItem> CREMATION_FURNACE = ITEMS.registerSimpleBlockItem("cremation_furnace", ModBlocks.CREMATION_FURNACE);

    public static final DeferredItem<BlockItem> URN = ITEMS.registerSimpleBlockItem("urn", ModBlocks.URN);

    public static final DeferredItem<StateBrandItem> STATE_BRAND = ITEMS.register("state_brand",
            () -> new StateBrandItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<CrownItem> MONARCH_CROWN = ITEMS.registerItem("monarch_crown", CrownItem::new);

    public static final DeferredItem<NewLifeStructureItem> NEW_LIFE_STRUCTURE = ITEMS.registerItem("new_life_structure", NewLifeStructureItem::new);

    public static final DeferredItem<NoteItem> NOTE = ITEMS.register("note",
            () -> new NoteItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<MemorialSignItem> MEMORIAL_SIGN = ITEMS.register("memorial_sign",
            () -> new MemorialSignItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<BlockItem> SMALL_TREASURY_CHEST = ITEMS.registerSimpleBlockItem("small_treasury_chest", ModBlocks.SMALL_TREASURY_CHEST);

    public static final DeferredItem<BlockItem> LARGE_TREASURY_CHEST = ITEMS.registerSimpleBlockItem("large_treasury_chest", ModBlocks.LARGE_TREASURY_CHEST);

    public static final DeferredItem<LockpickItem> LOCKPICK = ITEMS.register("lockpick",
            () -> new LockpickItem(new Item.Properties().stacksTo(16), 2400, "tooltip.onemanager.lockpick", 160, 300, 40, 0));

    public static final DeferredItem<LockpickItem> LOCKPICK_KIT = ITEMS.register("lockpick_kit",
            () -> new LockpickItem(new Item.Properties().stacksTo(4), 1200, "tooltip.onemanager.lockpick_kit", 120, 240, 30, 1));

    public static final DeferredRegister.Items LEGACY_ITEMS = DeferredRegister.createItems("monarchcrownmod");

    public static final DeferredItem<CrownItem> LEGACY_MONARCH_CROWN = LEGACY_ITEMS.registerItem("monarch_crown",
            props -> new CrownItem(props, ModArmorMaterials.LEGACY_MONARCH_CROWN_MATERIAL));
}
