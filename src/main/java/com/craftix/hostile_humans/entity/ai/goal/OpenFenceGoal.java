package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.PassageLease;
import net.minecraft.world.entity.Mob;

public class OpenFenceGoal extends FenceInteractGoal {
    private final boolean closeDoor;
    private int forgetTime;

    public OpenFenceGoal(Mob mob, boolean closeDoor) {
        super(mob);
        this.closeDoor = closeDoor;
    }

    @Override public boolean canContinueToUse() {
        return forgetTime > 0 && super.canContinueToUse();
    }

    @Override public void start() {
        super.start();
        forgetTime = 20;
        boolean wasOpen = isOpen();
        setOpen(true);
        if (closeDoor && !wasOpen && isOpen()) PassageLease.opened(mob, fencePosPos);
    }

    @Override public void stop() {
        // The shared bounded lease closes only passages this goal actually opened.
        // An interruption never shuts a door on another entity or closes a pre-opened door.
    }

    @Override public void tick() {
        --forgetTime;
        super.tick();
    }
}
