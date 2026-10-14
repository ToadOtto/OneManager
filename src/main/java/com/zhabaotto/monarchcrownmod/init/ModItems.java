package com.zhabaotto.monarchcrownmod.init;

import com.pcmanager.item.CrownItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = com.pcmanager.item.ModItems.LEGACY_ITEMS;

    public static final DeferredItem<CrownItem> MONARCH_CROWN = com.pcmanager.item.ModItems.LEGACY_MONARCH_CROWN;
}
