package com.craftix.hostile_humans.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class RandomStrollGoalFar
extends RandomStrollGoal {
    private boolean pendingPosition;
    public RandomStrollGoalFar(PathfinderMob p_25741_, double p_25742_, int p_25743_, boolean p_25744_) {
        super(p_25741_, p_25742_, p_25743_, p_25744_);
    }

    @Override
    public boolean canUse() {
        if (this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human
                && (human.hasOwner() || human.getPersistentData().hasUUID(club.someoneice.humangunner.HumanRelations.TEMP_OWNER)
                || club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human))) {
            pendingPosition = false;
            return false;
        }
        if (!pendingPosition && !super.canUse()) return false;
        pendingPosition = true;
        if (this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human
                && !club.someoneice.humangunner.BasicNavigationBudget.tryAcquire(human, 1)) return false;
        pendingPosition = false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return (!(this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human)
                || !club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human))
                && super.canContinueToUse();
    }

    protected Vec3 getPosition() {
        return DefaultRandomPos.getPos((PathfinderMob)this.mob, (int)30, (int)21);
    }
}

