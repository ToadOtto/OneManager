package com.onemanager.event;

import com.onemanager.OneManager;
import com.onemanager.command.GiveCrownCommand;
import com.onemanager.data.KnyazSavedData;
import com.onemanager.item.CrownItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = OneManager.MOD_ID)
public class CrownEvents {

    public static final String CROWN_GROUND_TICKS_TAG = "CrownGroundTicks";
    public static final String MONARCH_CROWN_DROPPED_TAG = "MonarchCrownDropped";
    public static final String WEARING_MONARCH_CROWN_TAG = "WearingMonarchCrown";
    public static final int MAX_GROUND_TICKS = 600; // 30 seconds (30 * 20)
    public static final int SCAN_INTERVAL_TICKS = 10;
    public static final int RECOVERY_CHECK_INTERVAL_TICKS = 200; // 10 seconds

    public static final List<Holder<MobEffect>> CROWN_BUFFS = List.of(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.REGENERATION,
            MobEffects.DIG_SPEED
    );

    public static void applyCrownBuffs(ServerPlayer player) {
        for (Holder<MobEffect> effect : CROWN_BUFFS) {
            MobEffectInstance current = player.getEffect(effect);
            if (current == null) {
                // Duration -1 (infinite) displays with infinity symbol without ticking timer
                player.addEffect(new MobEffectInstance(effect, -1, 0, false, false, true));
            } else if (!current.isInfiniteDuration() && current.getAmplifier() == 0) {
                player.removeEffect(effect);
                player.addEffect(new MobEffectInstance(effect, -1, 0, false, false, true));
            }
        }
    }

    public static void removeCrownBuffs(ServerPlayer player) {
        for (Holder<MobEffect> effect : CROWN_BUFFS) {
            MobEffectInstance current = player.getEffect(effect);
            if (current != null && current.isInfiniteDuration() && current.getAmplifier() == 0) {
                player.removeEffect(effect);
            }
        }
    }

    /**
     * Requirement: When a player wearing the crown dies, clear the helmet slot
     * to prevent keepInventory duplication and spawn the crown as a persistent ItemEntity.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
            if (headStack.getItem() instanceof CrownItem) {
                ItemStack dropStack = headStack.copy();

                // Clear the helmet slot immediately so keepInventory doesn't preserve it
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.getPersistentData().remove(WEARING_MONARCH_CROWN_TAG);
                removeCrownBuffs(player);

                ServerLevel serverLevel = player.serverLevel();
                ItemEntity itemEntity = new ItemEntity(
                        serverLevel,
                        player.getX(),
                        player.getY() + 0.5,
                        player.getZ(),
                        dropStack
                );

                itemEntity.setUnlimitedLifetime(); // Prevent vanilla 5-minute despawn
                itemEntity.setPickUpDelay(30);      // 1.5 seconds delay to prevent instant re-pickup
                itemEntity.getPersistentData().putInt(CROWN_GROUND_TICKS_TAG, 0);
                itemEntity.getPersistentData().putBoolean(MONARCH_CROWN_DROPPED_TAG, true);

                serverLevel.addFreshEntity(itemEntity);
            }
        }
    }

    /**
     * Server Tick Handling:
     * - If dropped crown lies on the ground for more than 30 seconds (600 ticks), return it to the Knyaz and equip.
     * - Prevent hoppers from sucking up dropped crown ItemEntity.
     * - If the crown completely disappeared from the world, regenerate and return/equip to the Knyaz.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        // 0. Regular 20-minute Tax / Day Cycle
        com.onemanager.data.CharacterWorldData worldData = com.onemanager.data.CharacterWorldData.get(server);
        if (worldData != null) {
            boolean dayCompleted = worldData.tickTax();
            if (dayCompleted) {
                int rate = worldData.getTaxRate();
                if (rate > 0) {
                    for (com.onemanager.data.PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
                        pd.decreaseTaxStatus(rate);
                    }
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        com.onemanager.data.PlayerCharacterData myPd = worldData.getPlayerData(p.getUUID());
                        long myStatus = myPd != null ? myPd.getTaxStatus() : -rate;
                        String statusFormatted = (myStatus > 0 ? "+" : "") + myStatus + " $PR";
                        ChatFormatting statusCol = myStatus < 0 ? ChatFormatting.RED : (myStatus >= rate ? ChatFormatting.GREEN : ChatFormatting.WHITE);

                        p.sendSystemMessage(
                                Component.literal("⚖ Списание налога: ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal("-" + rate + " $PR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                                        .append(Component.literal(" (Налоговый статус: ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal(statusFormatted).withStyle(statusCol, ChatFormatting.BOLD))
                                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY))
                        );
                    }
                }
                worldData.setDirty();
                com.onemanager.network.NetworkHandler.broadcastToAll(new com.onemanager.network.SyncAllCharactersPacket(worldData.save(new net.minecraft.nbt.CompoundTag()), false));
            }

            // 0b. Online State Brand Timer (count ticks ONLY while player is in the world)
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                com.onemanager.data.PlayerCharacterData pd = worldData.getPlayerData(sp.getUUID());
                if (pd != null) {
                    int granted = pd.tickOnlineBrand(1);
                    if (granted > 0) {
                        worldData.setDirty();
                        sp.sendSystemMessage(
                                Component.literal("📜 Недельные грамоты: вам начислено ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal("+" + granted + " Гос. Марок").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                                        .append(Component.literal(" на счёт за занятые профессии!").withStyle(ChatFormatting.GRAY))
                        );
                        com.onemanager.network.NetworkHandler.sendToPlayer(sp, new com.onemanager.network.SyncAllCharactersPacket(worldData.save(new net.minecraft.nbt.CompoundTag()), false));
                    }
                }
            }

            if (worldData.isWeekCompletedThisTick()) {
                worldData.setDirty();
                com.onemanager.network.NetworkHandler.broadcastToAll(new com.onemanager.network.SyncAllCharactersPacket(worldData.save(new net.minecraft.nbt.CompoundTag()), false));
            }
        }

        int currentTick = server.getTickCount();

        // 1. Check ground timer and hopper protection every SCAN_INTERVAL_TICKS (10 ticks)
        if (currentTick % SCAN_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof ItemEntity itemEntity && !itemEntity.isRemoved()) {
                        ItemStack stack = itemEntity.getItem();
                        if (stack.getItem() instanceof CrownItem) {
                            // Hopper Protection: if entity is on or above a hopper block, elevate it
                            if (level.getBlockState(itemEntity.blockPosition()).is(Blocks.HOPPER)
                                    || level.getBlockState(itemEntity.blockPosition().below()).is(Blocks.HOPPER)) {
                                itemEntity.setPos(itemEntity.getX(), Math.floor(itemEntity.getY()) + 1.2, itemEntity.getZ());
                                itemEntity.setDeltaMovement(0, 0.05, 0);
                            }

                            int groundTicks = itemEntity.getPersistentData().getInt(CROWN_GROUND_TICKS_TAG) + SCAN_INTERVAL_TICKS;
                            itemEntity.getPersistentData().putInt(CROWN_GROUND_TICKS_TAG, groundTicks);

                            if (groundTicks >= MAX_GROUND_TICKS) {
                                ItemStack returnStack = stack.copy();
                                itemEntity.discard();
                                returnCrownToKnyaz(server, returnStack);
                            }
                        }
                    }
                }
            }
        }

        // 2. Periodic check if crown completely disappeared from the world
        if (currentTick % RECOVERY_CHECK_INTERVAL_TICKS == 0) {
            KnyazSavedData data = KnyazSavedData.get(server);
            if (data != null && data.hasKnyaz() && !data.isPendingReturn()) {
                boolean crownExists = false;

                // Check online players' inventories
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (hasCrown(player)) {
                        crownExists = true;
                        break;
                    }
                }

                // Check ground entities across all dimensions
                if (!crownExists) {
                    for (ServerLevel level : server.getAllLevels()) {
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof ItemEntity itemEntity && !itemEntity.isRemoved()) {
                                if (itemEntity.getItem().getItem() instanceof CrownItem) {
                                    crownExists = true;
                                    break;
                                }
                            }
                        }
                        if (crownExists) {
                            break;
                        }
                    }
                }

                // If crown is nowhere to be found, restore it to the Knyaz
                if (!crownExists) {
                    ItemStack restoredCrown = CrownItem.createCrown(server.registryAccess(), data.getKnyazName());
                    returnCrownToKnyaz(server, restoredCrown);
                }
            }
        }
    }

    /**
     * Storage Restriction: Prevent placing Crown into any storage container GUI.
     * If placed in a non-player inventory slot, eject it back to the player.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean wearingCrown = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof CrownItem;
            boolean wasWearingCrown = player.getPersistentData().getBoolean(WEARING_MONARCH_CROWN_TAG);

            if (wearingCrown) {
                if (!wasWearingCrown) {
                    player.getPersistentData().putBoolean(WEARING_MONARCH_CROWN_TAG, true);
                }
                if (player.tickCount % 20 == 0 || !wasWearingCrown) {
                    applyCrownBuffs(player);
                }
            } else if (wasWearingCrown) {
                player.getPersistentData().remove(WEARING_MONARCH_CROWN_TAG);
                removeCrownBuffs(player);
            }

            if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
                for (Slot slot : player.containerMenu.slots) {
                    if (!(slot.container instanceof Inventory)) {
                        if (slot.hasItem() && slot.getItem().getItem() instanceof CrownItem) {
                            ItemStack extracted = slot.remove(slot.getItem().getCount());
                            giveOrEquipCrown(player, extracted);
                            player.containerMenu.broadcastChanges();
                            player.sendSystemMessage(
                                    Component.translatable("message.onemanager.cannot_store")
                                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Storage Restriction: Prevent dragging/stacking Crown onto container slots or bundles.
     */
    @SubscribeEvent
    public static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        if (event.getCarriedItem().getItem() instanceof CrownItem || event.getStackedOnItem().getItem() instanceof CrownItem) {
            if (!(event.getSlot().container instanceof Inventory)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Storage / Entity Restriction: Prevent placing Crown into Item Frames, Armor Stands, Allays, Minecarts, Boats.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack.getItem() instanceof CrownItem) {
            Entity target = event.getTarget();
            if (target instanceof ItemFrame
                    || target instanceof ArmorStand
                    || target instanceof Allay
                    || target instanceof AbstractMinecartContainer
                    || target instanceof ChestBoat) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.sendSystemMessage(
                            Component.translatable("message.onemanager.cannot_interact")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    );
                }
            }
        }
    }

    /**
     * Deliver pending crown when Knyaz logs in.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KnyazSavedData data = KnyazSavedData.get(player.server);
            if (data != null && data.isPendingReturn()) {
                boolean isKnyaz = (data.getKnyazUUID() != null && player.getUUID().equals(data.getKnyazUUID()))
                        || player.getGameProfile().getName().equalsIgnoreCase(data.getKnyazName());

                if (isKnyaz) {
                    ItemStack crown = CrownItem.createCrown(player.server.registryAccess(), data.getKnyazName());
                    giveOrEquipCrown(player, crown);
                    data.setPendingReturn(false);
                }
            }
        }
    }

    /**
     * Register commands.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GiveCrownCommand.register(event.getDispatcher());
    }

    /**
     * Helper to give or auto-equip crown on head if slot is free.
     */
    public static void giveOrEquipCrown(ServerPlayer player, ItemStack crownStack) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.setItemSlot(EquipmentSlot.HEAD, crownStack);
            player.sendSystemMessage(
                    Component.translatable("message.onemanager.crown_equipped")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            );
        } else {
            if (!player.getInventory().add(crownStack)) {
                player.drop(crownStack, false);
            }
            player.sendSystemMessage(
                    Component.translatable("message.onemanager.crown_returned")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            );
        }
    }

    /**
     * Helper to return crown to the current Knyaz.
     */
    public static void returnCrownToKnyaz(MinecraftServer server, ItemStack crownStack) {
        KnyazSavedData data = KnyazSavedData.get(server);
        if (data == null || !data.hasKnyaz()) {
            return;
        }

        ServerPlayer knyazPlayer = null;
        if (data.getKnyazUUID() != null) {
            knyazPlayer = server.getPlayerList().getPlayer(data.getKnyazUUID());
        }
        if (knyazPlayer == null && data.getKnyazName() != null && !data.getKnyazName().isEmpty()) {
            knyazPlayer = server.getPlayerList().getPlayerByName(data.getKnyazName());
        }

        if (knyazPlayer != null) {
            giveOrEquipCrown(knyazPlayer, crownStack);
            data.setPendingReturn(false);

            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.onemanager.crown_returned_broadcast", knyazPlayer.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        } else {
            // Knyaz is offline, queue return for when they log in
            data.setPendingReturn(true);
        }
    }

    /**
     * Helper to check if a player possesses the Monarch Crown in any inventory slot.
     */
    public static boolean hasCrown(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof CrownItem) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() instanceof CrownItem) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof CrownItem) return true;
        }
        return false;
    }
}
