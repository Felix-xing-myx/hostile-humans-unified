package com.craftix.hostile_humans.entity.ai.goal;

import java.util.EnumSet;
import dev.felix.hostilehumans.core.BudgetedSearch;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class FindWaterOnFireGoal
extends Goal {
    protected final PathfinderMob mob;
    private final double speedModifier;
    private final Level level;
    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private BudgetedSearch<BlockPos> search;
    private BlockPos searchOrigin;
    private int nextSearchTick;
    private static final int BLOCK_PROBES_PER_CHECK = 128;

    public FindWaterOnFireGoal(PathfinderMob p_25221_, double p_25222_) {
        this.mob = p_25221_;
        this.speedModifier = p_25222_;
        this.level = p_25221_.level();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human
                && club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human)) return false;
        if (this.mob.getEffect(MobEffects.FIRE_RESISTANCE) != null) {
            return false;
        }
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (!this.mob.isOnFire()) {
            search = null;
            searchOrigin = null;
            return false;
        }
        return this.setWantedPos();
    }

    protected boolean setWantedPos() {
        Vec3 hidePos = this.getHidePos();
        if (hidePos == null) {
            return false;
        }
        this.wantedX = hidePos.x;
        this.wantedY = hidePos.y;
        this.wantedZ = hidePos.z;
        return true;
    }

    public boolean canContinueToUse() {
        return (!(this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human)
                || !club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human))
                && !this.mob.getNavigation().isDone();
    }

    public void start() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    @Nullable
    protected Vec3 getHidePos() {
        BlockPos blockpos = this.mob.blockPosition();
        if (this.mob.tickCount < nextSearchTick) return null;
        if (search == null || searchOrigin.distSqr(blockpos) > 16.0D) {
            searchOrigin = blockpos.immutable();
            // Same complete 21x7x21 volume, but nearest blocks first and no
            // repeated full-volume scans every failed GoalSelector check.
            search = new BudgetedSearch<>(BlockPos.withinManhattan(searchOrigin, 10, 3, 10).iterator());
        }
        BlockPos water = search.firstMatching(BLOCK_PROBES_PER_CHECK,
                pos -> this.level.hasChunkAt(pos) && this.level.getBlockState(pos).is(Blocks.WATER));
        if (water != null) {
            search = null;
            nextSearchTick = this.mob.tickCount + 20;
            return Vec3.atBottomCenterOf(water);
        }
        if (!search.hasRemaining()) {
            search = null;
            nextSearchTick = this.mob.tickCount + 40 + Math.floorMod(this.mob.getId(), 20);
        }
        return null;
    }
}

