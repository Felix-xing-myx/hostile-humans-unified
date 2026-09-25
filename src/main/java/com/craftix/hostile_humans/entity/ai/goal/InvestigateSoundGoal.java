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
        if (this.mob.blockPosition().distSqr((Vec3i)this.pos) < 5.0 && this.hasInvestigated) {
            return false;
        }
        return this.canUse();
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
            this.mob.getNavigation().moveTo((double)this.pos.getX(), (double)this.pos.getY(), (double)this.pos.getZ(), this.speedModifier);
        }
    }

    public boolean isRunning() {
        return this.isRunning;
    }
}

