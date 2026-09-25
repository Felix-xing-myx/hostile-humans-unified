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
        // Breath recovery is handled by HumanMoveControl's direct upward
        // steering. Do not let this high-priority JUMP-only idle goal compete
        // with combat movement while a Human is pursuing or fighting a target.
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (!this.mob.prefersToFloat()) {
            return false;
        }
        if (!this.mob.hasSwimmingClearance()) {
            return false;
        }
        return this.mob.isInWater() || this.mob.isInLava();
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

