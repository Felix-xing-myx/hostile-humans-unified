package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.goal.DoorInteractHumanGoal;
import net.minecraft.world.entity.Mob;

public class OpenDoorsGoal
extends DoorInteractHumanGoal {
    private final boolean closeDoor;
    private int forgetTime;

    public OpenDoorsGoal(Mob mob, boolean closeDoor) {
        super(mob);
        this.closeDoor = closeDoor;
    }

    @Override
    public boolean canContinueToUse() {
        return this.closeDoor && this.forgetTime > 0 && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
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

