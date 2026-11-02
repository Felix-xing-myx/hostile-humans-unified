package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Survival without a known enemy. Known fights retain the existing adaptive defense/movement. */
public final class PressureReactionGoal extends Goal {
    private final Human human;
    private final ShieldDefenseController shields;
    public PressureReactionGoal(Human human) {
        this.human = human;
        shields = human.hhu$shieldDefense();
        setFlags(EnumSet.noneOf(Flag.class));
    }
    @Override public boolean canUse() {
        return shields.allowsPressureReaction();
    }
    @Override public void start() {
        shields.beginPressureReaction();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canContinueToUse() {
        return shields.continuesPressureReaction();
    }
    @Override public void tick() { shields.tickPressureReaction(); }
    @Override public void stop() {
        shields.endPressureReaction();
    }
}
