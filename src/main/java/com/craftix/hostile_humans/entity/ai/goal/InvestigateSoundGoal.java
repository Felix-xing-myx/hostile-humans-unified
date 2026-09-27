package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class InvestigateSoundGoal
extends Goal {
    protected final Mob mob;
    private final double speedModifier;
    private boolean hasInvestigated = false;
    @Nullable
    protected BlockPos pos = BlockPos.ZERO;
    private int calmDown;
    private boolean isRunning;
    private int failedNavigationTicks;
    private int nextPathAttemptTick;

    public InvestigateSoundGoal(Mob pMob, double pSpeedModifier) {
        this.mob = pMob;
        this.speedModifier = pSpeedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        if (this.mob instanceof Human human
                && club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human)) return false;
        if (this.mob.isSleeping()) {
            return false;
        }
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (this.calmDown > 0) {
            --this.calmDown;
            return false;
        }
        Mob mob = this.mob;
        if (mob instanceof Human) {
            Human investigator = (Human)mob;
            this.pos = investigator.investigateSound();
        }
        if (this.pos == BlockPos.ZERO) {
            return false;
        }
        return this.mob.blockPosition().distSqr((Vec3i)this.pos) < 1000.0;
    }

    public boolean canContinueToUse() {
        if (this.failedNavigationTicks >= 80) {
            return false;
        }
        if (this.mob instanceof Human human && human.wasNavigationGoalAbandoned(this.pos)) {
            return false;
        }
        if (this.mob.blockPosition().distSqr((Vec3i)this.pos) < 5.0 && this.hasInvestigated) {
            return false;
        }
        return !this.mob.isSleeping() && this.mob.getTarget() == null
                && this.mob.blockPosition().distSqr((Vec3i)this.pos) < 1000.0;
    }

    public void start() {
        Mob mob = this.mob;
        if (mob instanceof Human) {
            Human investigator = (Human)mob;
            this.pos = investigator.investigateSound();
            if (this.mob.level().getBlockState(this.pos).isAir()) {
                this.pos = this.pos.below();
            }
        }
        this.hasInvestigated = false;
        this.failedNavigationTicks = 0;
        this.nextPathAttemptTick = this.mob.tickCount;
    }

    public void stop() {
        this.pos = BlockPos.ZERO;
        Mob mob = this.mob;
        if (mob instanceof Human) {
            Human investigator = (Human)mob;
            investigator.setInvestigateSound(BlockPos.ZERO);
        }
        this.mob.getNavigation().stop();
        this.calmDown = InvestigateSoundGoal.reducedTickDelay((int)100);
        this.isRunning = false;
    }

    public void tick() {
        if (this.mob.blockPosition().distSqr((Vec3i)this.pos) < 5.0) {
            this.mob.getNavigation().stop();
            this.hasInvestigated = true;
        } else {
            if (this.mob.getNavigation().isDone()) {
                ++this.failedNavigationTicks;
                if (this.mob.tickCount >= this.nextPathAttemptTick) {
                    this.mob.getNavigation().moveTo(this.pos.getX(), this.pos.getY(),
                            this.pos.getZ(), this.speedModifier);
                    this.nextPathAttemptTick = this.mob.tickCount + 10;
                }
            } else {
                this.failedNavigationTicks = 0;
            }
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }
}

