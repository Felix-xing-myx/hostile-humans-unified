package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Anonymous arrow reaction uses the existing shield controller; never acquires a living target. */
public final class ProjectileReactionGoal extends Goal {
    private final Human human;
    private final ShieldDefenseController shields;
    private ShieldDefenseController.IncomingArrow incoming;
    public ProjectileReactionGoal(Human human) {
        this.human = human;
        this.shields = human.hhu$shieldDefense();
        setFlags(EnumSet.noneOf(Flag.class));
    }
    @Override public boolean canUse() {
        if (!shields.allowsProjectileReaction()) return false;
        incoming = shields.findIncomingArrow();
        return incoming != null;
    }
    @Override public void start() {
        shields.beginProjectileReaction(incoming);
    }
    @Override public boolean canContinueToUse() { return shields.continuesProjectileReaction(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { shields.tickProjectileReaction(); }
    @Override public void stop() {
        shields.endProjectileReaction();
        incoming = null;
    }
}
