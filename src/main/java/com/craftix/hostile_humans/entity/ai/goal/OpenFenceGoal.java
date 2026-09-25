package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.goal.FenceInteractGoal;
import net.minecraft.world.entity.Mob;

public class OpenFenceGoal
extends FenceInteractGoal {
    private final boolean closeFence;
    private int forgetTime;

    public OpenFenceGoal(Mob p_25678_, boolean p_25679_) {
        super(p_25678_);
        this.mob = p_25678_;
        this.closeFence = p_25679_;
    }

    @Override
    public boolean canContinueToUse() {
        return this.closeFence && this.forgetTime > 0 && super.canContinueToUse();
    }

    @Override
    public void start() {
        this.forgetTime = 20;
        this.setOpen(true);
    }

    public void stop() {
        this.setOpen(false);
    }

    @Override
    public void tick() {
        --this.forgetTime;
        super.tick();
    }
}

