package com.onemanager.item;

import com.onemanager.entity.MemorialSignEntity;
import com.onemanager.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class MemorialSignItem extends ItemFrameItem {

    public MemorialSignItem(Item.Properties properties) {
        super(ModEntities.MEMORIAL_SIGN.get(), properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos placePos = clickedPos.relative(face);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        if (player != null && !this.mayPlace(player, face, stack, placePos)) {
            return InteractionResult.FAIL;
        }

        MemorialSignEntity entity = new MemorialSignEntity(ModEntities.MEMORIAL_SIGN.get(), level, placePos, face);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            EntityType.updateCustomEntityTag(level, player, entity, customData);
        }

        if (entity.survives()) {
            if (!level.isClientSide) {
                entity.playPlacementSound();
                level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
                level.addFreshEntity(entity);
            }
            stack.shrink(1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.CONSUME;
        }
    }

    protected boolean mayPlace(Player player, Direction direction, ItemStack itemStack, BlockPos pos) {
        return !player.level().isOutsideBuildHeight(pos) && player.mayUseItemAt(pos, direction, itemStack);
    }
}
