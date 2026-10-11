package com.craftix.hostile_humans.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;

/** Idle glances use vanilla selection/timing; combat goals own target tracking. */
public final class HumanLookAtPlayerGoal extends LookAtPlayerGoal {
    private final Mob human;
    private long nextGlanceTick;

    public HumanLookAtPlayerGoal(Mob human) {
        super(human, Player.class, 4.0F, 0.02F);
        this.human = human;
    }

    @Override
    public boolean canUse() {
        return human.getTarget() == null
                && human.level().getGameTime() >= nextGlanceTick
                && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return human.getTarget() == null && super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        // Absolute server ticks: not shortened by the goal scheduler's tick rate.
        nextGlanceTick = human.level().getGameTime() + 100 + human.getRandom().nextInt(101);
    }
}
