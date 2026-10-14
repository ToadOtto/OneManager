package com.onemanager.client.event;

import com.onemanager.OneManager;
import com.onemanager.client.gui.CremationFurnaceMenu;
import com.onemanager.client.gui.RebirthScreen;
import com.onemanager.item.RemainsItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client event listeners for ScreenEvents.
 * 1. Intercepts DeathScreen to add the "Смерть и Новая Жизнь" button.
 * 2. Prevents moving Remains into containers or dropping them from container screens,
 *    except into the input slot (slot 0) of the Cremation Furnace.
 */
@EventBusSubscriber(modid = OneManager.MOD_ID, value = Dist.CLIENT)
public class ClientScreenEvents {

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof DeathScreen deathScreen) {
            int buttonWidth = 200;
            int buttonHeight = 20;
            int x = deathScreen.width / 2 - buttonWidth / 2;
            int y = deathScreen.height / 4 + 120; // Positioned cleanly below the vanilla buttons

            Component buttonText = Component.literal("Смерть и Новая Жизнь")
                    .withStyle(ChatFormatting.GOLD);

            Button rebirthButton = Button.builder(buttonText, btn -> {
                String defaultCause = "";
                if (Minecraft.getInstance().player != null) {
                    defaultCause = Minecraft.getInstance().player.getCombatTracker().getDeathMessage().getString();
                }
                Minecraft.getInstance().setScreen(new RebirthScreen(defaultCause));
            }).bounds(x, y, buttonWidth, buttonHeight).build();

            event.addListener(rebirthButton);
        }
    }

    /**
     * Intercepts mouse clicks inside container screens:
     * - Blocks placing held remains into non-player slots or dropping outside the screen,
     *   except into the Cremation Furnace remains input slot (index 0).
     * - Blocks Shift-clicking remains into a container, except into an empty Cremation Furnace slot 0.
     */
    @SubscribeEvent
    public static void onScreenMouseClickedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            Slot slot = containerScreen.getSlotUnderMouse();
            ItemStack carried = containerScreen.getMenu().getCarried();

            // 1. Cursor holding remains: prevent placing in container slots or dropping outside
            if (RemainsItem.isRemains(carried)) {
                if (containerScreen.getMenu() instanceof CremationFurnaceMenu) {
                    if (slot != null && (slot.container == player.getInventory() || slot.index == 0)) {
                        return;
                    }
                }
                if (slot == null || slot.container != player.getInventory()) {
                    event.setCanceled(true);
                    return;
                }
            }

            // 2. Shift-clicking remains in player inventory while a container is open
            if (slot != null && slot.container == player.getInventory() && RemainsItem.isRemains(slot.getItem())) {
                if (Screen.hasShiftDown() && containerScreen.getMenu() != player.inventoryMenu) {
                    if (containerScreen.getMenu() instanceof CremationFurnaceMenu furnaceMenu) {
                        if (furnaceMenu.getSlot(0).getItem().isEmpty()) {
                            return;
                        }
                    }
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * Intercepts key presses inside container screens:
     * - Blocks pressing Q (Drop) over remains.
     * - Blocks hotbar swap keys (1-9) into container slots when the hotbar slot contains remains,
     *   except into the Cremation Furnace remains input slot (index 0).
     */
    @SubscribeEvent
    public static void onScreenKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            Slot slot = containerScreen.getSlotUnderMouse();

            // Drop key (Q) over remains
            if (Minecraft.getInstance().options.keyDrop.matches(event.getKeyCode(), event.getScanCode())) {
                if (slot != null && RemainsItem.isRemains(slot.getItem())) {
                    event.setCanceled(true);
                    return;
                }
            }

            // Hotbar keys (1-9) swap into a container slot
            if (slot != null && slot.container != player.getInventory() && containerScreen.getMenu() != player.inventoryMenu) {
                if (containerScreen.getMenu() instanceof CremationFurnaceMenu && slot.index == 0) {
                    return;
                }
                for (int i = 0; i < 9; i++) {
                    if (Minecraft.getInstance().options.keyHotbarSlots[i].matches(event.getKeyCode(), event.getScanCode())) {
                        ItemStack hotbarItem = player.getInventory().getItem(i);
                        if (RemainsItem.isRemains(hotbarItem)) {
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}
