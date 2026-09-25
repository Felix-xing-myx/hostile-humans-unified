package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.goal.TrapdoorInteractGoal;
import net.minecraft.world.entity.Mob;

public class OpenTrapdoorGoal
extends TrapdoorInteractGoal {
    private final boolean closeDoor;
    private int forgetTime;

    public OpenTrapdoorGoal(Mob p_25678_, boolean p_25679_) {
        super(p_25678_);
        this.mob = p_25678_;
        this.closeDoor = p_25679_;
    }

    @Override
    public boolean canContinueToUse() {
        return this.closeDoor && this.forgetTime > 0 && super.canContinueToUse();
    }

    @Override
    public void start() {
        this.forgetTime = 40;
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

