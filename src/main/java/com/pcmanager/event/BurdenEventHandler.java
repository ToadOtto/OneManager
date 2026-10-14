package com.pcmanager.event;

import com.pcmanager.block.entity.CremationFurnaceBlockEntity;
import com.pcmanager.client.gui.CremationFurnaceMenu;
import com.pcmanager.data.ActiveCharacter;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.PlayerCharacterData;
import com.pcmanager.effect.ModEffects;
import com.pcmanager.item.AshesItem;
import com.pcmanager.item.RemainsItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.pcmanager.item.NoteItem;

public class BurdenEventHandler {
    private static final String KEY_LAST_EXHAUSTION = "pcmanager_last_exhaustion";

    /**
     * Prevents renaming or modifying Remains, Ashes, or Notes in the anvil.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (left == null || left.isEmpty()) return;
        if (RemainsItem.isRemains(left) || AshesItem.isAshes(left) || left.getItem() instanceof NoteItem) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevents tossing remains onto the ground (e.g. Q key).
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity() != null) {
            ItemStack item = event.getEntity().getItem();
            if (RemainsItem.isRemains(item) || AshesItem.isAshes(item)) {
                event.setCanceled(true);
                Player player = event.getPlayer();
                if (player != null) {
                    ItemStack stack = item.copy();
                    if (!player.getInventory().add(stack)) {
                        forceAddToInventory(player, stack);
                    }
                    player.inventoryMenu.broadcastChanges();
                    String msg = AshesItem.isAshes(stack) ? "Прах нельзя выбросить!" : "Останки нельзя выбросить!";
                    player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    /**
     * Cuts experience gains by 50% for players afflicted with Ancestor Burden.
     */
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player != null && player.hasEffect(ModEffects.ANCESTOR_BURDEN)) {
            int amount = event.getAmount();
            if (amount > 0) {
                int reduced = Math.max(1, (int) Math.floor(amount * 0.5));
                event.setAmount(reduced);
            }
        }
    }

    /**
     * Prevents milk or external curators from curing Ancestor Burden while active character has statusBurden = true.
     */
    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if ((event.getEffect().value() == ModEffects.ANCESTOR_BURDEN.value() || event.getEffect().is(ModEffects.ANCESTOR_BURDEN.getKey())) && event.getEntity() instanceof ServerPlayer player) {
            if (player.server != null) {
                CharacterWorldData worldData = CharacterWorldData.get(player.server);
                PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
                if (pd != null && pd.hasActiveCharacter() && pd.getActiveCharacter().isStatusBurden()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * Server-side player tick:
     * 1. Doubles food exhaustion rate for Ancestor Burden.
     * 2. Guards against remains being placed into container slots.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        // 1. Food exhaustion doubling
        if (player.hasEffect(ModEffects.ANCESTOR_BURDEN)) {
            float currentExhaustion = player.getFoodData().getExhaustionLevel();
            float lastExhaustion = player.getPersistentData().getFloat(KEY_LAST_EXHAUSTION);
            if (currentExhaustion > lastExhaustion) {
                float delta = currentExhaustion - lastExhaustion;
                player.getFoodData().addExhaustion(delta);
            }
            player.getPersistentData().putFloat(KEY_LAST_EXHAUSTION, player.getFoodData().getExhaustionLevel());
        } else if (player.getPersistentData().contains(KEY_LAST_EXHAUSTION)) {
            player.getPersistentData().remove(KEY_LAST_EXHAUSTION);
        }

        // 2. Container slot scanning
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
            boolean isFurnace = player.containerMenu instanceof CremationFurnaceMenu;
            boolean retrieved = false;
            for (Slot slot : player.containerMenu.slots) {
                // Allow remains in the cremation furnace input slot (slot 0) and ashes in output slot (slot 3)
                if (isFurnace && (slot.index == 0 || slot.index == 3)) {
                    continue;
                }
                if (slot.container instanceof CremationFurnaceBlockEntity && (slot.getContainerSlot() == 0 || slot.getContainerSlot() == 3)) {
                    continue;
                }
                if (slot.container != player.getInventory()) {
                    if (RemainsItem.isRemains(slot.getItem()) || AshesItem.isAshes(slot.getItem())) {
                        ItemStack extracted = slot.getItem().copy();
                        slot.set(ItemStack.EMPTY);
                        if (!player.getInventory().add(extracted)) {
                            forceAddToInventory(player, extracted);
                        }
                        retrieved = true;
                    }
                }
            }
            if (retrieved) {
                player.containerMenu.broadcastChanges();
                player.sendSystemMessage(Component.literal("Останки и прах нельзя переместить в контейнер!").withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * Verifies that closed containers do not retain any remains or ashes.
     */
    @SubscribeEvent
    public static void onPlayerContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player == null || event.getContainer() == player.inventoryMenu) {
            return;
        }

        boolean isFurnace = event.getContainer() instanceof CremationFurnaceMenu;
        boolean retrieved = false;
        for (Slot slot : event.getContainer().slots) {
            // Allow remains in slot 0 and ashes in slot 3 of cremation furnace
            if (isFurnace && (slot.index == 0 || slot.index == 3)) {
                continue;
            }
            if (slot.container instanceof CremationFurnaceBlockEntity && (slot.getContainerSlot() == 0 || slot.getContainerSlot() == 3)) {
                continue;
            }
            if (slot.container != player.getInventory()) {
                if (RemainsItem.isRemains(slot.getItem()) || AshesItem.isAshes(slot.getItem())) {
                    ItemStack extracted = slot.getItem().copy();
                    slot.set(ItemStack.EMPTY);
                    if (!player.getInventory().add(extracted)) {
                        forceAddToInventory(player, extracted);
                    }
                    retrieved = true;
                }
            }
        }
        if (retrieved) {
            event.getContainer().broadcastChanges();
            player.sendSystemMessage(Component.literal("Останки и прах нельзя оставить в контейнере!").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Ensures the Ancestor Burden effect is active upon login if the character has statusBurden = true.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAndApplyBurden(player);

            if (player.server != null) {
                CharacterWorldData worldData = CharacterWorldData.get(player.server);
                PlayerCharacterData playerData = worldData.getPlayerData(player.getUUID());
                if (playerData != null && !playerData.getPendingRemains().isEmpty()) {
                    for (CompoundTag remTag : playerData.getPendingRemains()) {
                        ItemStack rem = ItemStack.parseOptional(player.server.registryAccess(), remTag);
                        forceAddToInventory(player, rem);
                        if (AshesItem.isAshes(rem)) {
                            String deadName = AshesItem.getCharacterName(rem);
                            player.sendSystemMessage(Component.literal("Урна с прахом " + deadName + " была осквернена, пока вы отсутствовали!").withStyle(ChatFormatting.RED));
                        } else {
                            CompoundTag tag = RemainsItem.getCustomTag(rem);
                            String deadName = tag != null ? tag.getString(RemainsItem.KEY_DEAD_CHAR_NAME) : "предка";
                            player.sendSystemMessage(Component.literal("Могила " + deadName + " была осквернена, пока вы отсутствовали!").withStyle(ChatFormatting.RED));
                        }
                    }
                    playerData.clearPendingRemains();
                    worldData.setDirty();
                    player.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    /**
     * Ensures the Ancestor Burden effect is preserved after respawn if the character has statusBurden = true.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAndApplyBurden(player);
        }
    }

    private static void checkAndApplyBurden(ServerPlayer player) {
        if (player.server == null) return;
        CharacterWorldData worldData = CharacterWorldData.get(player.server);
        PlayerCharacterData playerData = worldData.getPlayerData(player.getUUID());
        if (playerData != null && playerData.hasActiveCharacter()) {
            ActiveCharacter active = playerData.getActiveCharacter();
            if (active.isStatusBurden()) {
                if (!player.hasEffect(ModEffects.ANCESTOR_BURDEN)) {
                    player.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                }
            } else {
                if (player.hasEffect(ModEffects.ANCESTOR_BURDEN)) {
                    player.removeEffect(ModEffects.ANCESTOR_BURDEN);
                }
            }
        }
    }

    /**
     * Helper to place a stack into player inventory, replacing an existing non-remains, non-ashes item if full.
     * Replaced items are dropped safely near the player.
     */
    public static void forceAddToInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            return;
        }

        // Search for a non-remains, non-ashes slot to replace
        int replaceSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!RemainsItem.isRemains(current) && !AshesItem.isAshes(current)) {
                replaceSlot = i;
                break;
            }
        }

        if (replaceSlot != -1) {
            ItemStack old = player.getInventory().getItem(replaceSlot);
            player.getInventory().setItem(replaceSlot, stack);
            if (!old.isEmpty()) {
                player.drop(old, false);
            }
        } else {
            player.getInventory().setItem(0, stack);
        }
    }
}
