package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

public class HumanFloatGoal
extends Goal {
    private final Human mob;

    public HumanFloatGoal(Human p_25230_) {
        this.mob = p_25230_;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP));
        p_25230_.getNavigation().setCanFloat(true);
    }

    public boolean canUse() {
        // Water ascent is steered smoothly by HumanMoveControl. Repeatedly
        // calling jump() under water caused an upward launch every few ticks.
        return this.mob.getTarget() == null && this.mob.isInLava();
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (this.mob.getRandom().nextFloat() < 0.8f) {
            this.mob.getJumpControl().jump();
        }
    }
}

