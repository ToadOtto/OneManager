package com.onemanager.heist;

import com.onemanager.item.LockpickItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class LockpickSession {
    private final UUID playerUuid;
    private final BlockPos chestPos;
    private final ResourceKey<Level> dimensionKey;
    private final Vec3 startPlayerPos;
    private final InteractionHand hand;
    private final LockpickItem lockpickItem;

    private int totalTicks;
    private int elapsedTicks;
    private int mistakes;

    // QTE state
    private int qteCounter = 0;
    private int activeQteId = 0;
    private int activeTargetDigit = 0;
    private int activeQteDeadline = 0;
    private int nextQteTick;

    public LockpickSession(UUID playerUuid, BlockPos chestPos, ResourceKey<Level> dimensionKey,
                           Vec3 startPlayerPos, InteractionHand hand, LockpickItem lockpickItem,
                           RandomSource random) {
        this.playerUuid = playerUuid;
        this.chestPos = chestPos;
        this.dimensionKey = dimensionKey;
        this.startPlayerPos = startPlayerPos;
        this.hand = hand;
        this.lockpickItem = lockpickItem;
        this.totalTicks = lockpickItem.getDurationTicks();
        this.elapsedTicks = 0;
        this.mistakes = 0;
        this.nextQteTick = HeistMath.nextQteDelay(random, lockpickItem.getQteIntervalMin(), lockpickItem.getQteIntervalMax());
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public BlockPos getChestPos() {
        return chestPos;
    }

    public ResourceKey<Level> getDimensionKey() {
        return dimensionKey;
    }

    public Vec3 getStartPlayerPos() {
        return startPlayerPos;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public LockpickItem getLockpickItem() {
        return lockpickItem;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void incrementElapsedTicks() {
        this.elapsedTicks++;
    }

    public void addPenaltyTicks(int penalty) {
        this.totalTicks += penalty;
    }

    public boolean isComplete() {
        return elapsedTicks >= totalTicks;
    }

    public boolean isQteActive() {
        return activeQteId > 0;
    }

    public int getActiveQteId() {
        return activeQteId;
    }

    public int getActiveTargetDigit() {
        return activeTargetDigit;
    }

    public boolean shouldStartQte() {
        return !isQteActive() && elapsedTicks >= nextQteTick && (totalTicks - elapsedTicks > 30);
    }

    public int generateQte(RandomSource random) {
        this.qteCounter++;
        this.activeQteId = this.qteCounter;
        this.activeTargetDigit = 1 + random.nextInt(9);
        this.activeQteDeadline = elapsedTicks + lockpickItem.getQteWindowTicks();
        return this.activeQteId;
    }

    public boolean isQteTimedOut() {
        return isQteActive() && elapsedTicks >= activeQteDeadline;
    }

    public void clearQte(RandomSource random) {
        this.activeQteId = 0;
        this.activeTargetDigit = 0;
        this.activeQteDeadline = 0;
        this.nextQteTick = elapsedTicks + HeistMath.nextQteDelay(random, lockpickItem.getQteIntervalMin(), lockpickItem.getQteIntervalMax());
    }

    public int recordMistake() {
        this.mistakes++;
        return this.mistakes;
    }

    public int getMistakes() {
        return mistakes;
    }

    public boolean hasExceededMistakes() {
        return mistakes > lockpickItem.getMaxMistakes();
    }
}
