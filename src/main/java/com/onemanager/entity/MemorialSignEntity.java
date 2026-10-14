package com.onemanager.entity;

import com.mojang.authlib.GameProfile;
import com.onemanager.item.AshesItem;
import com.onemanager.item.ModItems;
import com.onemanager.item.RemainsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Memorial Sign Entity (onemanager:memorial_sign).
 * Sized like a vanilla sign board, hanging on a block face like an item frame.
 * When right-clicked with Remains or Ashes, it captures the deceased character's head,
 * name, and death date. When destroyed, all stored character data resets.
 */
public class MemorialSignEntity extends ItemFrame {

    private static final EntityDataAccessor<String> DATA_CHAR_NAME =
            SynchedEntityData.defineId(MemorialSignEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> DATA_DEATH_DATE =
            SynchedEntityData.defineId(MemorialSignEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(MemorialSignEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_IS_BOUND =
            SynchedEntityData.defineId(MemorialSignEntity.class, EntityDataSerializers.BOOLEAN);

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault());

    // Client cache for resolving player skin profile
    private ResolvableProfile cachedProfile = null;
    private UUID lastProfileUuid = null;
    private String lastProfileName = null;

    public MemorialSignEntity(EntityType<? extends ItemFrame> type, Level level) {
        super(type, level);
    }

    public MemorialSignEntity(EntityType<? extends ItemFrame> type, Level level, BlockPos pos, Direction direction) {
        super(type, level, pos, direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHAR_NAME, "");
        builder.define(DATA_DEATH_DATE, 0L);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_IS_BOUND, false);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double dX = 0.40625D; // half width (0.8125 total ~ 13 px from Blockbench)
        double dY = 0.28125D; // half height (0.5625 total ~ 9 px from Blockbench)
        double dZ = 0.03125D; // half depth (0.0625 total ~ 1 px from Blockbench)

        double offset = 0.46875D; // 15/32 from block center to surface

        if (direction.getAxis().isHorizontal()) {
            Direction ccw = direction.getCounterClockWise();
            double wX = Math.abs(ccw.getStepX()) * dX + Math.abs(direction.getStepX()) * dZ;
            double wZ = Math.abs(ccw.getStepZ()) * dX + Math.abs(direction.getStepZ()) * dZ;
            return new AABB(
                    x - direction.getStepX() * offset - wX,
                    y - dY,
                    z - direction.getStepZ() * offset - wZ,
                    x - direction.getStepX() * offset + wX,
                    y + dY,
                    z - direction.getStepZ() * offset + wZ
            );
        } else {
            return new AABB(
                    x - dX,
                    y - direction.getStepY() * offset - dZ,
                    z - dY,
                    x + dX,
                    y - direction.getStepY() * offset + dZ,
                    z + dY
            );
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Right-clicking with Remains or Ashes binds the character's memory
        if (RemainsItem.isRemains(stack) || AshesItem.isAshes(stack)) {
            if (!this.level().isClientSide) {
                CompoundTag tag = RemainsItem.isRemains(stack)
                        ? RemainsItem.getCustomTag(stack)
                        : AshesItem.getCustomTag(stack);

                String name = "";
                long deathDate = 0L;
                UUID owner = null;

                if (tag != null) {
                    if (tag.contains(RemainsItem.KEY_DEAD_CHAR_NAME)) {
                        name = tag.getString(RemainsItem.KEY_DEAD_CHAR_NAME).trim();
                    }
                    if (tag.contains(RemainsItem.KEY_DEATH_DATE)) {
                        deathDate = tag.getLong(RemainsItem.KEY_DEATH_DATE);
                    }
                    if (tag.hasUUID(RemainsItem.KEY_OWNER_UUID)) {
                        owner = tag.getUUID(RemainsItem.KEY_OWNER_UUID);
                    }
                }

                if (name.isEmpty()) {
                    name = AshesItem.isAshes(stack) ? AshesItem.getCharacterName(stack) : "Безымянный";
                }
                if (owner == null) {
                    owner = player.getUUID();
                }

                this.setBound(true);
                this.setCharName(name);
                this.setDeathDate(deathDate);
                this.setOwnerUUID(owner);

                // Ritual audio feedback
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8F, 1.2F);

                // Ritual soul and enchantment particles
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.1, this.getZ(),
                            12, 0.15, 0.15, 0.15, 0.02);
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.1, this.getZ(),
                            16, 0.25, 0.25, 0.25, 0.5);
                }

                String dateStr = deathDate > 0
                        ? DATE_FORMATTER.format(Instant.ofEpochMilli(deathDate))
                        : "Неизвестно";
                player.sendSystemMessage(Component.literal("§6На ритуальной табличке запечатлен лик и память: §e" + name + " §7(" + dateStr + ")"));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Right-clicking bound sign with empty hand shows info
        if (this.isBound()) {
            if (!this.level().isClientSide) {
                String dateStr = getDeathDate() > 0
                        ? DATE_FORMATTER.format(Instant.ofEpochMilli(getDeathDate()))
                        : "Неизвестно";
                player.sendSystemMessage(Component.literal("§6Ритуальная табличка: §e" + getCharName() + " §7(Погиб(ла): " + dateStr + ")"));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (!this.isRemoved() && !this.level().isClientSide) {
            this.kill();
            this.markHurt();
            this.dropItem(source.getEntity());
        }
        return true;
    }

    @Override
    public void dropItem(@Nullable Entity brokenEntity) {
        this.playSound(SoundEvents.ITEM_FRAME_BREAK, 1.0F, 1.0F);
        if (brokenEntity instanceof Player player && player.hasInfiniteMaterials()) {
            return;
        }
        // "При разрушении всё сбрасывается" - drops a clean item without NBT
        this.spawnAtLocation(new ItemStack(ModItems.MEMORIAL_SIGN.get()));
    }

    @Override
    public ItemStack getFrameItemStack() {
        return new ItemStack(ModItems.MEMORIAL_SIGN.get());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("isBound", this.isBound());
        tag.putString("deadCharName", this.getCharName());
        tag.putLong("deathDate", this.getDeathDate());
        if (this.getOwnerUUID() != null) {
            tag.putUUID("ownerUUID", this.getOwnerUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setBound(tag.getBoolean("isBound"));
        this.setCharName(tag.getString("deadCharName"));
        this.setDeathDate(tag.getLong("deathDate"));
        if (tag.hasUUID("ownerUUID")) {
            this.setOwnerUUID(tag.getUUID("ownerUUID"));
        } else {
            this.setOwnerUUID(null);
        }
    }

    public boolean isBound() {
        return this.entityData.get(DATA_IS_BOUND);
    }

    public void setBound(boolean bound) {
        this.entityData.set(DATA_IS_BOUND, bound);
    }

    public String getCharName() {
        return this.entityData.get(DATA_CHAR_NAME);
    }

    public void setCharName(String name) {
        this.entityData.set(DATA_CHAR_NAME, name != null ? name : "");
    }

    public long getDeathDate() {
        return this.entityData.get(DATA_DEATH_DATE);
    }

    public void setDeathDate(long deathDate) {
        this.entityData.set(DATA_DEATH_DATE, deathDate);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
    }

    public String getFormattedDeathDate() {
        long d = getDeathDate();
        return d > 0 ? DATE_FORMATTER.format(Instant.ofEpochMilli(d)) : "Неизвестно";
    }

    /**
     * Resolves game profile for player skin rendering. Cached client-side.
     */
    public ResolvableProfile getResolvableProfile() {
        UUID uuid = getOwnerUUID();
        String name = getCharName();
        if (cachedProfile == null || !Objects.equals(uuid, lastProfileUuid) || !Objects.equals(name, lastProfileName)) {
            this.lastProfileUuid = uuid;
            this.lastProfileName = name;
            GameProfile gp = new GameProfile(uuid != null ? uuid : UUID.randomUUID(), name != null ? name : "");
            this.cachedProfile = new ResolvableProfile(gp);
        }
        return cachedProfile;
    }
}
