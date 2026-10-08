package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Owns idle movement so vanilla wandering cannot override a hired order. */
final class SoldierOrderGoal extends Goal {
    private final Human human;

    SoldierOrderGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return human.hasOwner() && (SoldierOrder.get(human) != SoldierOrder.BODYGUARD
                || BodyguardProtection.owner(human) == null || SoldierOrder.isReturningFromRetreat(human))
                && !human.isFleeing && human.getTarget() == null
                && !human.isInLava() && !human.isSeekingShore() && !human.isNoAi()
                && !human.isSleeping() && !human.isPassenger()
                && !HumanGunner.isManualInventoryOpen(human);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override public void stop() { SoldierOrder.stopMovement(human); }

    @Override
    public void tick() {
        SoldierOrder.tickMovement(human);
    }
}
