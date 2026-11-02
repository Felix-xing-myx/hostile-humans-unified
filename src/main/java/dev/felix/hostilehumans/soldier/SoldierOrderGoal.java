package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.observer.NavigationDebugService;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Owns idle movement so vanilla wandering cannot override a hired order. */
public final class SoldierOrderGoal extends Goal {
    private final Human human;

    public SoldierOrderGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return (human.hasOwner() || SoldierOrder.isGarrison(human)) && (SoldierOrder.get(human) != SoldierOrder.BODYGUARD
                || BodyguardProtection.owner(human) == null || SoldierOrder.isReturningFromRetreat(human))
                && !human.isFleeing && (human.getTarget() == null || human.guardCombatState.returning())
                && !SoldierOrder.yieldsReturnToDefense(human)
                && !human.isInLava() && !human.isSeekingShore() && !human.isNoAi()
                && !human.isSleeping() && !human.isPassenger()
                && !HumanRuntime.isManualInventoryOpen(human) && !NavigationDebugService.controls(human);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override public void stop() { SoldierOrder.stopMovement(human); NaturalRoute.stopOwned(human); }

    @Override
    public void tick() {
        SoldierOrder.tickMovement(human);
    }
}
