package com.onemanager.item;

import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.OpenRebirthScreenS2CPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class NewLifeStructureItem extends Item {
    public NewLifeStructureItem() {
        this(new Item.Properties());
    }

    public NewLifeStructureItem(Properties properties) {
        super(properties.stacksTo(64).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CharacterWorldData worldData = CharacterWorldData.get(serverPlayer.server);
            PlayerCharacterData pd = worldData.getPlayerData(serverPlayer.getUUID());
            ActiveCharacter active = pd != null ? pd.getActiveCharacter() : null;

            String name = active != null ? active.getName() : "";
            int age = active != null ? active.getAge() : 18;
            String race = active != null ? active.getRace() : "";
            String appearance = active != null ? active.getAppearance() : "";
            String desc = active != null ? active.getDescription() : "";

            NetworkHandler.sendToPlayer(serverPlayer, new OpenRebirthScreenS2CPacket(true, name, age, race, appearance, desc));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.onemanager.new_life_structure").withStyle(ChatFormatting.GRAY));
    }
}
