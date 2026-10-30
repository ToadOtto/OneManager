package com.onemanager.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.onemanager.block.entity.TreasuryChestBlockEntity;
import com.onemanager.client.gui.CharHistoryScreen;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.data.TreasuryChestEntry;
import com.onemanager.heist.HeistManager;
import com.onemanager.item.LockpickItem;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.OpenTreasuryReasonScreenS2CPacket;
import com.onemanager.network.SyncAllCharactersPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TreasuryChestBlock extends BaseEntityBlock {
    public static final MapCodec<TreasuryChestBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    Codec.BOOL.fieldOf("large").forGetter(TreasuryChestBlock::isLarge)
            ).apply(instance, TreasuryChestBlock::new)
    );

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    private final boolean large;

    public TreasuryChestBlock(Properties properties, boolean large) {
        super(properties);
        this.large = large;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isLarge() {
        return large;
    }

    public int getCapacity() {
        return large ? TreasuryChestEntry.LARGE_CAPACITY : TreasuryChestEntry.SMALL_CAPACITY;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TreasuryChestBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        int cap = getCapacity();
        tooltipComponents.add(Component.literal("Вместимость: " + String.format("%,d", cap).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("Доступно только Князю").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            String dim = serverLevel.dimension().location().toString();
            String placerName = "Неизвестно";
            if (placer instanceof ServerPlayer sp) {
                PlayerCharacterData pd = worldData.getPlayerData(sp.getUUID());
                placerName = (pd != null && pd.hasActiveCharacter()) ? pd.getActiveCharacter().getName() : sp.getScoreboardName();
            }

            TreasuryChestEntry entry = worldData.registerTreasuryChest(dim, pos, large, placerName);
            int index = worldData.getTreasuryChests().indexOf(entry) + 1;

            String pName = placer instanceof ServerPlayer sp ? sp.getScoreboardName() : placerName;
            String actionDesc = "Установка хранилища #" + index + " (" + entry.getTypeTitle() + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "])";
            worldData.logTransaction(placerName, pName, actionDesc, worldData.getTreasury());
            worldData.setDirty();

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));

            if (placer instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("🏦 Хранилище казны #" + index + " установлено! ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("(+" + entry.getCapacity() + " $PR к вместимости казны)")
                                .withStyle(ChatFormatting.GREEN)));
            }
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            if (!worldData.canAccessTreasury(player.getUUID())) {
                return 0.0F;
            }
        } else if (level.getBlockEntity(pos) != null) {
            if (!CharHistoryScreen.canLocalPlayerAccessTreasury()) {
                return 0.0F;
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof LockpickItem) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                HeistManager.tryStartLockpick(serverPlayer, hand, pos);
            }
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            if (!worldData.canAccessTreasury(serverPlayer.getUUID())) {
                serverPlayer.sendSystemMessage(Component.literal("❌ Открыть Сундук Казны может только Князь!")
                        .withStyle(ChatFormatting.RED));
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
                return InteractionResult.FAIL;
            }

            // Ensure chest is registered in worldData
            String dim = serverLevel.dimension().location().toString();
            TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, pos);
            if (entry == null) {
                PlayerCharacterData pd = worldData.getPlayerData(serverPlayer.getUUID());
                String charName = (pd != null && pd.hasActiveCharacter()) ? pd.getActiveCharacter().getName() : serverPlayer.getScoreboardName();
                entry = worldData.registerTreasuryChest(dim, pos, large, charName);
            }

            // If chest was tampered with during heist vacuum, Knyaz discovers it immediately
            if (entry.getPendingStolen() > 0) {
                HeistManager.onChestDiscovered(serverLevel.getServer(), entry, serverPlayer);
            }

            // If seals were broken, Knyaz inspecting the chest restores seals and capacity
            if (entry.isSealBroken()) {
                entry.setSealBroken(false);
                worldData.setDirty();
                NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                serverPlayer.sendSystemMessage(Component.literal("🛡️ Вы восстановили государственные пломбы на хранилище. Вместимость казны возвращена!")
                        .withStyle(ChatFormatting.GREEN));
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TreasuryChestBlockEntity chestBe) {
                chestBe.setStoredAmountFromWorldData((int) entry.getAmount());
                level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
                serverPlayer.openMenu(chestBe, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
            if (!worldData.canAccessTreasury(serverPlayer.getUUID())) {
                return state;
            }

            String dim = serverLevel.dimension().location().toString();
            TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, pos);
            int stored = 0;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TreasuryChestBlockEntity chestBe) {
                stored = chestBe.getStoredAmount();
                chestBe.setStoredAmountFromWorldData(0);
            } else if (entry != null) {
                stored = (int) entry.getEffectiveAmount();
            }

            if (entry != null && entry.getPendingStolen() > 0) {
                HeistManager.onChestDiscovered(serverLevel.getServer(), entry, serverPlayer);
            }

            // Drop all stored Sprota items
            dropSprotaAmount(serverLevel, pos, stored);

            // Drop the chest block item if not creative
            if (!serverPlayer.isCreative()) {
                Containers.dropItemStack(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, new ItemStack(this.asItem()));
            }

            // Remove chest from worldData
            worldData.removeTreasuryChestAt(dim, pos);
            worldData.setDirty();

            // Register pending demolition reason and open reason modal on client
            String posStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            worldData.setPendingTreasuryReason(serverPlayer.getUUID(),
                    new CharacterWorldData.PendingTreasuryReason(stored, true, posStr, this.large ? "Большой Сундук Казны" : "Малый Сундук Казны"));

            NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
            NetworkHandler.sendToPlayer(serverPlayer, new OpenTreasuryReasonScreenS2CPacket(stored, true, posStr));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                CharacterWorldData worldData = CharacterWorldData.get(serverLevel);
                String dim = serverLevel.dimension().location().toString();
                TreasuryChestEntry entry = worldData.getTreasuryChestAt(dim, pos);
                if (entry != null) {
                    int stored = (int) entry.getAmount();
                    dropSprotaAmount(serverLevel, pos, stored);
                    worldData.removeTreasuryChestAt(dim, pos);
                    worldData.setDirty();
                    NetworkHandler.broadcastToAll(new SyncAllCharactersPacket(worldData.save(new CompoundTag()), false));
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public static void dropSprotaAmount(Level level, BlockPos pos, int totalAmount) {
        if (totalAmount <= 0) return;
        int remaining = totalAmount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            ItemStack drop = new ItemStack(com.oneshop.item.ModItems.SPROTA.get(), batch);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, drop);
            remaining -= batch;
        }
    }
}
