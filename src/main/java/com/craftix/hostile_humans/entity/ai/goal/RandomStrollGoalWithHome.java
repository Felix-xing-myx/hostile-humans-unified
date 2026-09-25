package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.Vec3;

public class RandomStrollGoalWithHome
extends RandomStrollGoal {
    Human human;

    public RandomStrollGoalWithHome(Human human, double p_25742_, int p_25743_, boolean p_25744_) {
        super((PathfinderMob)human, p_25742_, p_25743_, p_25744_);
        this.human = human;
    }

    @Override
    public boolean canUse() {
        return !club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)
                && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)
                && super.canContinueToUse();
    }

    protected Vec3 getPosition() {
        BlockPos homePos = this.human.getHomePos();
        if (homePos != null && homePos.distToCenterSqr((Position)this.human.position()) > 100.0) {
            return new Vec3((double)homePos.getX(), (double)homePos.getY(), (double)homePos.getZ());
        }
        return super.getPosition();
    }
}

