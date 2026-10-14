package com.zhabaotto.monarchcrownmod.init;

import com.onemanager.item.CrownItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = com.onemanager.item.ModItems.LEGACY_ITEMS;

    public static final DeferredItem<CrownItem> MONARCH_CROWN = com.onemanager.item.ModItems.LEGACY_MONARCH_CROWN;
}
