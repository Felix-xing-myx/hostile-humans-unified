package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.entity.entities.ChestExtension;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LookForChestGoal
extends Goal {
    private static final Map<Level, ChestState> STATES = new java.util.WeakHashMap<>();
    private static final class ChestState {
        final Map<ChestKey, UUID> reserved = new HashMap<>();
        final Map<ChestKey, Long> expires = new HashMap<>();
        final Map<ChestKey, Long> cooldown = new HashMap<>();
        long nextCleanup;
    }
    private ChestState state() { return STATES.computeIfAbsent(mob.level(), key -> new ChestState()); }
    protected final Human mob;
    private final double speedModifier;
    @Nullable
    protected BlockPos pos = UNREACHABLE;
    protected int timer = 0;
    private boolean chestOpened;
    public static final BlockPos UNREACHABLE = new BlockPos(0, -9999, 0);

    public LookForChestGoal(Human pMob, double pSpeedModifier) {
        this.mob = pMob;
        this.speedModifier = pSpeedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.mob)) return false;
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel server)) return false;
        this.cleanupExpiredState();
        if (this.mob.lookForChestCooldown > 0) {
            return false;
        }
        if (this.mob.getTarget() != null || this.mob.isSleeping() || this.mob.isFleeing) {
            return false;
        }
        if (this.pos != UNREACHABLE) {
            return true;
        }
        if ((double)this.mob.getRandom().nextFloat() >= (Double)Config.chestOpenChance.get()) {
            this.mob.lookForChestCooldown = this.mob.getRandom().nextInt(600, 1800);
            return false;
        }
        // Inspect existing block entities, not 16,000 individual blocks or unloaded chunks.
        BlockPos origin = mob.blockPosition();
        int inspected = 0;
        for (int cx = (origin.getX() - 20) >> 4; cx <= (origin.getX() + 19) >> 4; cx++) {
            for (int cz = (origin.getZ() - 20) >> 4; cz <= (origin.getZ() + 19) >> 4; cz++) {
                var chunk = server.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (++inspected > 1024) break;
                    BlockPos chestPos = blockEntity.getBlockPos();
                    if (Math.abs(chestPos.getX() - origin.getX()) > 20
                            || Math.abs(chestPos.getZ() - origin.getZ()) > 20
                            || Math.abs(chestPos.getY() - origin.getY()) > 5
                            || !(blockEntity.getBlockState().getBlock() instanceof ChestBlock)
                            || !canClaimChest(chestPos)) continue;
                    pos = chestPos.immutable();
                    timer = mob.getRandom().nextInt(100, 300);
                    return true;
                }
                if (inspected > 1024) break;
            }
            if (inspected > 1024) break;
        }
        this.mob.lookForChestCooldown = 24000;
        return false;
    }

    public boolean canContinueToUse() {
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.mob)
                || this.pos == UNREACHABLE || this.mob.getTarget() != null || this.mob.isSleeping() || this.mob.isFleeing) {
            return false;
        }
        if (this.mob.blockPosition().distSqr((Vec3i)this.pos) < 5.0) {
            return this.timer > 0;
        }
        return this.mob.blockPosition().distSqr((Vec3i)this.pos) < 1000.0;
    }

    public void start() {
        this.chestOpened = false;
        this.reserveChest();
    }

    public void stop() {
        BlockEntity blockEntity;
        if (this.chestOpened && (blockEntity = this.mob.level().getBlockEntity(this.pos)) instanceof ChestExtension) {
            ChestExtension ch = (ChestExtension)blockEntity;
            ch.hostileHumans$setForcedOpen(false);
            ch.openersCounter().decrementOpeners(null, this.mob.level(), this.pos, this.mob.level().getBlockState(this.pos));
        }
        this.releaseChest(this.timer <= 0);
        this.chestOpened = false;
        this.mob.getNavigation().stop();
        if (this.timer <= 0) {
            this.pos = UNREACHABLE;
        }
    }

    public void tick() {
        if (this.mob.blockPosition().distSqr((Vec3i)this.pos) < 5.0) {
            this.mob.getNavigation().stop();
            BlockEntity blockEntity = this.mob.level().getBlockEntity(this.pos);
            if (blockEntity instanceof ChestExtension) {
                ChestExtension ch = (ChestExtension)blockEntity;
                if (!this.chestOpened) {
                    ch.openersCounter().incrementOpeners(null, this.mob.level(), this.pos, this.mob.level().getBlockState(this.pos));
                    ch.hostileHumans$setForcedOpen(true);
                    this.chestOpened = true;
                }
                --this.timer;
                if (this.timer <= 0) {
                    ch.hostileHumans$setForcedOpen(false);
                    ch.openersCounter().decrementOpeners(null, this.mob.level(), this.pos, this.mob.level().getBlockState(this.pos));
                    this.chestOpened = false;
                    this.pos = UNREACHABLE;
                    this.mob.lookForChestCooldown = this.mob.getRandom().nextInt(6000, 12000);
                }
            }
        } else {
            this.mob.getNavigation().moveTo((double)this.pos.getX(), (double)this.pos.getY(), (double)this.pos.getZ(), this.speedModifier);
        }
    }

    private boolean canClaimChest(BlockPos chestPos) {
        ChestKey key = this.chestKey(chestPos);
        long gameTime = this.mob.level().getGameTime();
        Long cooldownUntil = state().cooldown.get(key);
        if (cooldownUntil != null && cooldownUntil > gameTime) {
            return false;
        }
        UUID reservedBy = state().reserved.get(key);
        Long reservedUntil = state().expires.get(key);
        return reservedBy == null || reservedUntil == null || reservedUntil <= gameTime || reservedBy.equals(this.mob.getUUID());
    }

    private void reserveChest() {
        if (this.pos == UNREACHABLE) {
            return;
        }
        ChestKey key = this.chestKey(this.pos);
        long reserveUntil = this.mob.level().getGameTime() + (long)this.timer + 40L;
        state().reserved.put(key, this.mob.getUUID());
        state().expires.put(key, reserveUntil);
    }

    private void releaseChest(boolean addCooldown) {
        if (this.pos == UNREACHABLE) {
            return;
        }
        ChestKey key = this.chestKey(this.pos);
        UUID reservedBy = state().reserved.get(key);
        if (reservedBy != null && reservedBy.equals(this.mob.getUUID())) {
            state().reserved.remove(key);
            state().expires.remove(key);
            if (addCooldown) {
                state().cooldown.put(key, this.mob.level().getGameTime() + (long)this.mob.getRandom().nextInt(3600, 7200));
            }
        }
    }

    private void cleanupExpiredState() {
        long gameTime = this.mob.level().getGameTime();
        ChestState state = state();
        if (gameTime < state.nextCleanup) return;
        state.nextCleanup = gameTime + 20;
        state().expires.entrySet().removeIf(entry -> (Long)entry.getValue() <= gameTime);
        state().reserved.entrySet().removeIf(entry -> !state().expires.containsKey(entry.getKey()));
        state().cooldown.entrySet().removeIf(entry -> (Long)entry.getValue() <= gameTime);
    }

    private ChestKey chestKey(BlockPos chestPos) {
        return new ChestKey((ResourceKey<Level>)this.mob.level().dimension(), new BlockPos(chestPos.getX(), chestPos.getY(), chestPos.getZ()));
    }

    private record ChestKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}

