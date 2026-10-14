package com.onemanager.block;

import com.onemanager.block.entity.TombstoneBlockEntity;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.effect.ModEffects;
import com.onemanager.event.BurdenEventHandler;
import com.onemanager.item.RemainsItem;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.SyncAllCharactersPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tombstone block (onemanager:tombstone).
 * Holds deceased remains in TombstoneBlockEntity.
 * Handles burying, epitaph reading, and breaking/desecration mechanics.
 */
public class TombstoneBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // VoxelShape for cross tombstone: base + vertical post (up to 30) + horizontal crossbar
    private static final VoxelShape SHAPE_BASE = Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0);
    private static final VoxelShape SHAPE_POST = Block.box(6.0, 1.0, 6.0, 10.0, 30.0, 10.0);
    private static final VoxelShape SHAPE_CROSSBAR_NS = Block.box(0.0, 19.0, 6.25, 16.0, 23.0, 9.75);
    private static final VoxelShape SHAPE_CROSSBAR_EW = Block.box(6.25, 19.0, 0.0, 9.75, 23.0, 16.0);

    private static final VoxelShape SHAPE_NORTH_SOUTH = Shapes.or(SHAPE_BASE, SHAPE_POST, SHAPE_CROSSBAR_NS);
    private static final VoxelShape SHAPE_EAST_WEST = Shapes.or(SHAPE_BASE, SHAPE_POST, SHAPE_CROSSBAR_EW);

    public TombstoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);
        return (dir == Direction.NORTH || dir == Direction.SOUTH) ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TombstoneBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (com.onemanager.item.AshesItem.isAshes(stack)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("Прах нельзя поместить в могилу — для него требуется урна.")
                        .withStyle(ChatFormatting.RED));
            }
            return ItemInteractionResult.FAIL;
        }

        if (RemainsItem.isRemains(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TombstoneBlockEntity tombstone) {
                if (!tombstone.isOccupied()) {
                    tombstone.setStoredRemains(stack);
                    stack.shrink(1);

                    level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 0.9F);

                    if (player.getServer() != null) {
                        CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                        UUID ownerUuid = tombstone.getOwnerUUID();
                        String deadName = tombstone.getDeadCharName();
                        CompoundTag remTag = RemainsItem.getCustomTag(tombstone.getStoredRemains());
                        long deathDate = remTag != null && remTag.contains(RemainsItem.KEY_DEATH_DATE) ? remTag.getLong(RemainsItem.KEY_DEATH_DATE) : 0;

                        if (ownerUuid != null) {
                            PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                            if (ownerPd != null) {
                                for (com.onemanager.data.DeadCharacter dc : ownerPd.getCharacterHistory()) {
                                    if (dc.getName().equals(deadName) && (deathDate == 0 || dc.getDeathDate() == deathDate)) {
                                        dc.setBuried(true);
                                        break;
                                    }
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

                    player.removeEffect(ModEffects.ANCESTOR_BURDEN);

                    player.sendSystemMessage(Component.literal("Предок упокоен. Бремя Предков спало с вашей души.")
                            .withStyle(ChatFormatting.DARK_PURPLE));
                    return ItemInteractionResult.CONSUME;
                }
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TombstoneBlockEntity tombstone && tombstone.isOccupied()) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal(tombstone.getEpitaph()).withStyle(ChatFormatting.GOLD));
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
        if (be instanceof TombstoneBlockEntity tombstone) {
            if (!tombstone.isOccupied()) {
                player.sendSystemMessage(Component.literal("Могила пуста").withStyle(ChatFormatting.GRAY));
            } else {
                player.sendSystemMessage(Component.literal(tombstone.getEpitaph()).withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TombstoneBlockEntity tombstone && tombstone.isOccupied()) {
                ItemStack storedRemains = tombstone.getStoredRemains();
                UUID ownerUuid = tombstone.getOwnerUUID();
                String deadName = tombstone.getDeadCharName();
                CompoundTag remTag = RemainsItem.getCustomTag(storedRemains);
                long deathDate = remTag != null && remTag.contains(RemainsItem.KEY_DEATH_DATE) ? remTag.getLong(RemainsItem.KEY_DEATH_DATE) : 0;

                if (ownerUuid != null && player.getServer() != null) {
                    CharacterWorldData worldData = CharacterWorldData.get(player.getServer());
                    PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                    if (ownerPd != null) {
                        for (com.onemanager.data.DeadCharacter dc : ownerPd.getCharacterHistory()) {
                            if (dc.getName().equals(deadName) && (deathDate == 0 || dc.getDeathDate() == deathDate)) {
                                dc.setBuried(false);
                                break;
                            }
                        }
                    }
                }

                if (ownerUuid != null && ownerUuid.equals(player.getUUID())) {
                    // Owner broke their own tombstone:
                    // Force remains into owner inventory (NO drops on ground)
                    BurdenEventHandler.forceAddToInventory(player, storedRemains);
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

                    player.sendSystemMessage(Component.literal("Вы потревожили могилу предка. Бремя Предков возвращается к вам!")
                            .withStyle(ChatFormatting.RED));
                } else {
                    // Desecration by non-owner:
                    // Remains NEVER drop into the world.
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
                            BurdenEventHandler.forceAddToInventory(onlineOwner, storedRemains);
                            onlineOwner.inventoryMenu.broadcastChanges();
                            onlineOwner.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                            onlineOwner.sendSystemMessage(Component.literal("Могила " + deadName + " была осквернена!")
                                     .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                        } else if (ownerData != null) {
                            CompoundTag pendingRemTag = new CompoundTag();
                            storedRemains.save(player.getServer().registryAccess(), pendingRemTag);
                            ownerData.addPendingRemains(pendingRemTag);
                            worldData.setDirty();
                        }
                    }
                }
                tombstone.setStoredRemains(ItemStack.EMPTY);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TombstoneBlockEntity tombstone && tombstone.isOccupied()) {
                ItemStack storedRemains = tombstone.getStoredRemains();
                UUID ownerUuid = tombstone.getOwnerUUID();
                String deadName = tombstone.getDeadCharName();
                CompoundTag remTag = RemainsItem.getCustomTag(storedRemains);
                long deathDate = remTag != null && remTag.contains(RemainsItem.KEY_DEATH_DATE) ? remTag.getLong(RemainsItem.KEY_DEATH_DATE) : 0;

                if (ownerUuid != null && level.getServer() != null) {
                    CharacterWorldData worldData = CharacterWorldData.get(level.getServer());
                    PlayerCharacterData ownerPd = worldData.getPlayerData(ownerUuid);
                    if (ownerPd != null) {
                        for (com.onemanager.data.DeadCharacter dc : ownerPd.getCharacterHistory()) {
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
                        BurdenEventHandler.forceAddToInventory(onlineOwner, storedRemains);
                        onlineOwner.inventoryMenu.broadcastChanges();
                        onlineOwner.addEffect(new MobEffectInstance(ModEffects.ANCESTOR_BURDEN, -1, 0, false, false, true));
                        onlineOwner.sendSystemMessage(Component.literal("Могила " + deadName + " была разрушена!")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                    } else if (ownerPd != null) {
                        CompoundTag pendingRemTag = new CompoundTag();
                        storedRemains.save(level.getServer().registryAccess(), pendingRemTag);
                        ownerPd.addPendingRemains(pendingRemTag);
                        worldData.setDirty();
                    }
                }
                tombstone.setStoredRemains(ItemStack.EMPTY);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
