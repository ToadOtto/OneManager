package com.onemanager;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(OneManager.MOD_ID)
public class OneManager {
    public static final String MOD_ID = "onemanager";
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

    public OneManager(IEventBus modEventBus, Dist dist) {
        modEventBus.addListener(this::commonSetup);

        com.onemanager.block.ModBlocks.BLOCKS.register(modEventBus);
        com.onemanager.block.entity.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.onemanager.entity.ModEntities.ENTITIES.register(modEventBus);
        com.onemanager.item.ModItems.ITEMS.register(modEventBus);
        com.onemanager.item.ModItems.LEGACY_ITEMS.register(modEventBus);
        com.onemanager.item.ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        com.onemanager.item.ModArmorMaterials.LEGACY_ARMOR_MATERIALS.register(modEventBus);
        com.onemanager.item.ModDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        com.onemanager.item.ModDataComponents.LEGACY_DATA_COMPONENT_TYPES.register(modEventBus);
        com.onemanager.item.ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.onemanager.effect.ModEffects.EFFECTS.register(modEventBus);
        com.onemanager.client.gui.ModMenuTypes.MENUS.register(modEventBus);
        com.onemanager.recipe.ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);

        modEventBus.addListener(com.onemanager.network.NetworkHandler::registerPayloadHandlers);
        modEventBus.addListener(OneManager::registerCapabilities);

        NeoForge.EVENT_BUS.register(com.onemanager.command.ModCommands.class);
        NeoForge.EVENT_BUS.register(com.onemanager.event.BurdenEventHandler.class);
        NeoForge.EVENT_BUS.register(com.onemanager.event.CrownEvents.class);
        NeoForge.EVENT_BUS.register(com.onemanager.event.WorldProgressionEvents.class);

        if (dist.isClient()) {
            com.onemanager.client.KeyBindings.register(modEventBus);
            modEventBus.addListener(OneManager::registerScreens);
            modEventBus.addListener(OneManager::registerEntityRenderers);
        }

        LOGGER.info("OneManager NeoForge 1.21.1 mod initialized.");
    }

    private static void registerEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.onemanager.entity.ModEntities.MEMORIAL_SIGN.get(), com.onemanager.client.renderer.MemorialSignRenderer::new);
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.onemanager.block.entity.ModBlockEntities.CREMATION_FURNACE.get(),
                (be, side) -> be.getItemHandler(side)
        );
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(com.onemanager.client.gui.ModMenuTypes.CREMATION_FURNACE.get(), com.onemanager.client.gui.CremationFurnaceScreen::new);
        event.register(com.onemanager.client.gui.ModMenuTypes.TOMB_WORKBENCH.get(), com.onemanager.client.gui.TombWorkbenchScreen::new);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("OneManager common setup complete.");
    }
}
