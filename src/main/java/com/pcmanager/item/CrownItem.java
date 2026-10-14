package com.pcmanager.item;

import com.pcmanager.data.KnyazSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class CrownItem extends ArmorItem {

    public CrownItem() {
        this(new Item.Properties(), ModArmorMaterials.MONARCH_CROWN_MATERIAL);
    }

    public CrownItem(Properties properties) {
        this(properties, ModArmorMaterials.MONARCH_CROWN_MATERIAL);
    }

    public CrownItem(Properties properties, Holder<ArmorMaterial> material) {
        super(
                material,
                ArmorItem.Type.HELMET,
                properties.fireResistant().rarity(Rarity.EPIC)
        );
    }

    public static ItemStack createCrown(@Nullable RegistryAccess registryAccess, @Nullable String knyazName) {
        ItemStack stack = new ItemStack(ModItems.MONARCH_CROWN.get());
        if (registryAccess != null) {
            registryAccess.lookup(Registries.ENCHANTMENT).ifPresent(enchantments -> {
                enchantments.get(Enchantments.BINDING_CURSE).ifPresent(curse -> {
                    stack.enchant(curse, 1);
                });
            });
        }
        if (knyazName != null && !knyazName.isEmpty()) {
            setKnyazName(stack, knyazName);
        }
        return stack;
    }

    public static String getKnyazName(ItemStack stack) {
        String name = stack.get(ModDataComponents.KNYAZ_NAME.get());
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String legacyName = stack.get(ModDataComponents.LEGACY_KNYAZ_NAME.get());
        return legacyName != null ? legacyName : "";
    }

    public static void setKnyazName(ItemStack stack, String knyazName) {
        stack.set(ModDataComponents.KNYAZ_NAME.get(), knyazName);
        stack.set(ModDataComponents.LEGACY_KNYAZ_NAME.get(), knyazName);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        String knyazName = getKnyazName(stack);

        if (knyazName == null || knyazName.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.pcmanager.no_knyaz").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.pcmanager.current_knyaz",
                    Component.literal(knyazName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            ).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            // Ensure Binding Curse is present
            level.registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(enchantments -> {
                enchantments.get(Enchantments.BINDING_CURSE).ifPresent(curse -> {
                    if (stack.getEnchantmentLevel(curse) <= 0) {
                        stack.enchant(curse, 1);
                    }
                });
            });

            // Check if equipped in the helmet slot
            if (player.getItemBySlot(EquipmentSlot.HEAD) == stack) {
                // Apply monarch crown buffs: Speed I, Resistance I, Regeneration I, Haste I (infinite display)
                com.pcmanager.event.CrownEvents.applyCrownBuffs(player);

                KnyazSavedData data = KnyazSavedData.get(player.server);
                if (data != null) {
                    if (!player.getUUID().equals(data.getKnyazUUID())) {
                        // New Knyaz has equipped the crown
                        data.setKnyaz(player.getGameProfile().getName(), player.getUUID());
                        setKnyazName(stack, player.getGameProfile().getName());

                        com.pcmanager.data.CharacterWorldData worldData = com.pcmanager.data.CharacterWorldData.get(player.server);
                        if (worldData != null) {
                            worldData.setKnyaz(player.getGameProfile().getName(), player.getUUID());
                            com.pcmanager.network.NetworkHandler.broadcastToAll(new com.pcmanager.network.SyncAllCharactersPacket(worldData.save(new net.minecraft.nbt.CompoundTag()), false));
                        }

                        Component broadcast = Component.translatable(
                                "message.pcmanager.new_knyaz",
                                player.getDisplayName()
                        ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

                        player.server.getPlayerList().broadcastSystemMessage(broadcast, false);
                    } else {
                        // Keep component synced
                        if (!Objects.equals(getKnyazName(stack), data.getKnyazName())) {
                            setKnyazName(stack, data.getKnyazName());
                        }
                    }
                }
            }
        }
    }
}
