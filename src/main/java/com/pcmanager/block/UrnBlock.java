package com.pcmanager.block;

import com.pcmanager.block.entity.ModBlockEntities;
import com.pcmanager.block.entity.UrnBlockEntity;
import com.pcmanager.data.CharacterWorldData;
import com.pcmanager.data.DeadCharacter;
import com.pcmanager.data.PlayerCharacterData;
import com.pcmanager.effect.ModEffects;
import com.pcmanager.event.BurdenEventHandler;
import com.pcmanager.item.AshesItem;
import com.pcmanager.item.RemainsItem;
import com.pcmanager.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import com.pcmanager.network.SyncAllCharactersPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Urn block (pcmanager:urn).
 * Uses flower pot geometry as placeholder.
 * Right-click with Ashes places the ashes inside (max 1).
 * Right-click inspects who rests inside.
 * Drops stored ashes when broken.
 */
public class UrnBlock extends Block implements EntityBlock {
    // Standard flower pot shape: 6x6x6 centered box
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);

    public UrnBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UrnBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (RemainsItem.isRemains(stack)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("В урну можно поместить только прах! Сначала кремируйте останки в печи.")
                        .withStyle(ChatFormatting.RED));
            }
            return ItemInteractionResult.FAIL;
        }

        if (AshesItem.isAshes(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UrnBlockEntity urn) {
                if (!urn.hasAshes()) {
                    ItemStack placed = stack.split(1);
                    urn.setStoredAshes(placed);

                    level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);

                    if (player.getServer() != null) {
                        CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                        UUID ownerUuid = urn.getOwnerUUID();
                        String deadName = urn.getAshesName();
                        long deathDate = AshesItem.getDeathDate(placed);

                        if (ownerUuid != null) {
                            PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                            if (ownerPd != null) {
                                for (DeadCharacter dc : ownerPd.getCharacterHistory()) {
                                    if (dc.getName().equals(deadName) && (deathDate == 0 || dc.getDeathDate() == deathDate)) {
                                        dc.setBuried(true);
                                        break;
                                    }
                                }
                                boolean hasUnburied = ownerPd.getCharacterHistory().stream().anyMatch(d -> !d.isBuried());
                                if (!hasUnburied && ownerPd.hasActiveCharacter()) {
                                    ownerPd.getActiveCharacter().setStatusBurden(false);
                                }
                                ServerPlayer onlineOwner = player.getServer().getPlayerList().getPlayer(ownerUuid);
                                if (onlineOwner != null && !onlineOwner.getUUID().equals(player.getUUID()) && !hasUnburied) {
                                    onlineOwner.removeEffect(ModEffects.ANCESTOR_BURDEN);
                                    onlineOwner.sendSystemMessage(Component.literal("Прах вашего предка (" + deadName + ") был упокоен в урне! Бремя Предков спало с вашей души.")
                                            .withStyle(ChatFormatting.DARK_PURPLE));
                                }
                            }
                        }

                        PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
                        if (pd != null && pd.hasActiveCharacter()) {
                            if (ownerUuid == null || ownerUuid.equals(player.getUUID())) {
                                pd.getActiveCharacter().setStatusBurden(false);
                            }
                        }
                        worldData.setDirty();
                        NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    }

                    if (urn.getOwnerUUID() == null || urn.getOwnerUUID().equals(player.getUUID())) {
                        player.removeEffect(ModEffects.ANCESTOR_BURDEN);
                        player.sendSystemMessage(Component.literal("Прах предка упокоен в урне. Бремя Предков спало с вашей души.")
                                .withStyle(ChatFormatting.DARK_PURPLE));
                    } else {
                        player.sendSystemMessage(Component.literal("Прах " + urn.getAshesName() + " упокоен в урне.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                    return ItemInteractionResult.CONSUME;
                } else {
                    player.sendSystemMessage(Component.literal("В этой урне уже покоится прах: " + urn.getAshesName())
                            .withStyle(ChatFormatting.RED));
                    return ItemInteractionResult.FAIL;
                }
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof UrnBlockEntity urn && urn.hasAshes()) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("В урне покоится: " + urn.getAshesName()).withStyle(ChatFormatting.GOLD));
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof UrnBlockEntity urn) {
            if (urn.hasAshes()) {
                player.sendSystemMessage(Component.literal("В урне покоится: " + urn.getAshesName()).withStyle(ChatFormatting.GOLD));
            } else {
                player.sendSystemMessage(Component.literal("Урна пуста.").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UrnBlockEntity urn && urn.hasAshes()) {
                ItemStack storedAshes = urn.getStoredAshes();
                UUID ownerUuid = urn.getOwnerUUID();
                String deadName = urn.getAshesName();
                long deathDate = AshesItem.getDeathDate(storedAshes);

                if (ownerUuid != null && player.getServer() != null) {
                    CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                    PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                    if (ownerPd != null) {
                        for (DeadCharacter dc : ownerPd.getCharacterHistory()) {
                            if (dc.getName().equals(deadName) && (deathDate == 0 || dc.getDeathDate() == deathDate)) {
                                dc.setBuried(false);
                                break;
                            }
                        }
                    }
                }

                if (ownerUuid != null && ownerUuid.equals(player.getUUID())) {
                    // Owner broke their own urn:
                    // Force ashes into owner inventory (NO drops on ground)
                    BurdenEventHandler.forceAddToInventory(player, storedAshes);
                    player.inventoryMenu.broadcastChanges();

                    player.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                    if (player.getServer() != null) {
                        CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                        PlayerCharacterData pd = worldData.getPlayerData(player.getUUID());
                        if (pd != null && pd.hasActiveCharacter()) {
                            pd.getActiveCharacter().setStatusBurden(true);
                            worldData.setDirty();
                            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                        }
                    }

                    player.sendSystemMessage(Component.literal("Вы потревожили урну с прахом предка. Бремя Предков возвращается к вам!")
                            .withStyle(ChatFormatting.RED));
                } else {
                    // Desecration by non-owner:
                    // Ashes NEVER drop into the world.
                    // Directly routed to owner (online or queued for offline login).
                    if (ownerUuid != null && player.getServer() != null) {
                        CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                        PlayerCharacterData ownerData = worldData.getPlayerData(ownerUuid);
                        if (ownerData != null && ownerData.hasActiveCharacter()) {
                            ownerData.getActiveCharacter().setStatusBurden(true);
                            worldData.setDirty();
                            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                        }

                        ServerPlayer onlineOwner = player.getServer().getPlayerList().getPlayer(ownerUuid);
                        if (onlineOwner != null) {
                            BurdenEventHandler.forceAddToInventory(onlineOwner, storedAshes);
                            onlineOwner.inventoryMenu.broadcastChanges();
                            onlineOwner.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                            onlineOwner.sendSystemMessage(Component.literal("Урна с прахом " + deadName + " была осквернена!")
                                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        } else if (ownerData != null) {
                            CompoundTag pendingAshesTag = new CompoundTag();
                            storedAshes.save(player.getServer().registryAccess(), pendingAshesTag);
                            ownerData.addPendingRemains(pendingAshesTag);
                            worldData.setDirty();
                        }
                    } else if (ownerUuid == null) {
                        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, storedAshes);
                    }
                }
                urn.setStoredAshes(ItemStack.EMPTY);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UrnBlockEntity urn && urn.hasAshes()) {
                ItemStack storedAshes = urn.getStoredAshes();
                UUID ownerUuid = urn.getOwnerUUID();
                String deadName = urn.getAshesName();
                long deathDate = AshesItem.getDeathDate(storedAshes);

                if (ownerUuid != null && level.getServer() != null) {
                    CharacterWorldData worldData = CharacterWorldData.get(level.getServer());
                    PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                    if (ownerPd != null) {
                        for (DeadCharacter dc : ownerPd.getCharacterHistory()) {
                            if (dc.getName().equals(deadName) && (deathDate == 0 || dc.getDeathDate() == deathDate)) {
                                dc.setBuried(false);
                                break;
                            }
                        }
                        if (ownerPd.hasActiveCharacter()) {
                            ownerPd.getActiveCharacter().setStatusBurden(true);
                        }
                        worldData.setDirty();
                        NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                    }

                    ServerPlayer onlineOwner = level.getServer().getPlayerList().getPlayer(ownerUuid);
                    if (onlineOwner != null) {
                        BurdenEventHandler.forceAddToInventory(onlineOwner, storedAshes);
                        onlineOwner.inventoryMenu.broadcastChanges();
                        onlineOwner.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                        onlineOwner.sendSystemMessage(Component.literal("Урна с прахом " + deadName + " была разрушена!")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                    } else if (ownerPd != null) {
                        CompoundTag pendingTag = new CompoundTag();
                        storedAshes.save(level.getServer().registryAccess(), pendingTag);
                        ownerPd.addPendingRemains(pendingTag);
                        worldData.setDirty();
                    }
                } else if (ownerUuid == null) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, storedAshes);
                }
                urn.setStoredAshes(ItemStack.EMPTY);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
