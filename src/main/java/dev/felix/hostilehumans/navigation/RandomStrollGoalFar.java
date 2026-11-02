package dev.felix.hostilehumans.navigation;

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
        if (this.mob instanceof dev.felix.hostilehumans.entity.Human human
                && (human.hasOwner() || human.getPersistentData().hasUUID(dev.felix.hostilehumans.faction.HumanRelations.TEMP_OWNER)
                || dev.felix.hostilehumans.soldier.SoldierOrder.isHoldingPosition(human))) {
            pendingPosition = false;
            return false;
        }
        if (!pendingPosition && !super.canUse()) return false;
        pendingPosition = true;
        if (this.mob instanceof dev.felix.hostilehumans.entity.Human human
                && !dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1)) return false;
        pendingPosition = false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return (!(this.mob instanceof dev.felix.hostilehumans.entity.Human human)
                || !dev.felix.hostilehumans.soldier.SoldierOrder.isHoldingPosition(human))
                && super.canContinueToUse();
    }

    protected Vec3 getPosition() {
        return DefaultRandomPos.getPos((PathfinderMob)this.mob, (int)30, (int)21);
    }
}

