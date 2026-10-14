package com.pcmanager;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(PCManager.MOD_ID)
public class PCManager {
    public static final String MOD_ID = "pcmanager";
    public static final Logger LOGGER = LogUtils.getLogger();

    static {
        sanitizeLog4jProperty("log4j.configurationFile");
        sanitizeLog4jProperty("log4j2.configurationFile");
    }

    private static void sanitizeLog4jProperty(String prop) {
        String val = System.getProperty(prop);
        if (val != null && !val.isBlank() && !val.contains("://") && !val.startsWith("file:")) {
            try {
                System.setProperty(prop, java.nio.file.Path.of(val).toUri().toString());
            } catch (Exception ignored) {}
        }
    }

    public PCManager(IEventBus modEventBus, Dist dist) {
        modEventBus.addListener(this::commonSetup);

        com.pcmanager.block.ModBlocks.BLOCKS.register(modEventBus);
        com.pcmanager.block.entity.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.pcmanager.entity.ModEntities.ENTITIES.register(modEventBus);
        com.pcmanager.item.ModItems.ITEMS.register(modEventBus);
        com.pcmanager.item.ModItems.LEGACY_ITEMS.register(modEventBus);
        com.pcmanager.item.ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        com.pcmanager.item.ModArmorMaterials.LEGACY_ARMOR_MATERIALS.register(modEventBus);
        com.pcmanager.item.ModDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        com.pcmanager.item.ModDataComponents.LEGACY_DATA_COMPONENT_TYPES.register(modEventBus);
        com.pcmanager.item.ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.pcmanager.effect.ModEffects.EFFECTS.register(modEventBus);
        com.pcmanager.client.gui.ModMenuTypes.MENUS.register(modEventBus);
        com.pcmanager.recipe.ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(com.pcmanager.network.NetworkHandler::registerPayloadHandlers);
        modEventBus.addListener(PCManager::registerCapabilities);

        NeoForge.EVENT_BUS.register(com.pcmanager.command.ModCommands.class);
        NeoForge.EVENT_BUS.register(com.pcmanager.event.BurdenEventHandler.class);
        NeoForge.EVENT_BUS.register(com.pcmanager.event.CrownEvents.class);
        NeoForge.EVENT_BUS.register(com.pcmanager.event.WorldProgressionEvents.class);

        if (dist.isClient()) {
            com.pcmanager.client.KeyBindings.register(modEventBus);
            modEventBus.addListener(PCManager::registerScreens);
            modEventBus.addListener(PCManager::registerEntityRenderers);
        }

        LOGGER.info("P&C Manager NeoForge 1.21.1 mod initialized.");
    }

    private static void registerEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.pcmanager.entity.ModEntities.MEMORIAL_SIGN.get(), com.pcmanager.client.renderer.MemorialSignRenderer::new);
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.pcmanager.block.entity.ModBlockEntities.CREMATION_FURNACE.get(),
                (be, side) -> be.getItemHandler(side)
        );
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(com.pcmanager.client.gui.ModMenuTypes.CREMATION_FURNACE.get(), com.pcmanager.client.gui.CremationFurnaceScreen::new);
        event.register(com.pcmanager.client.gui.ModMenuTypes.TOMB_WORKBENCH.get(), com.pcmanager.client.gui.TombWorkbenchScreen::new);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("P&C Manager common setup complete.");
    }
}
