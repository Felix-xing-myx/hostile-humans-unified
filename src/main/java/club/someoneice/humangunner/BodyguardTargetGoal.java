package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Reserves target selection in BODYGUARD so generic nearest-target goals cannot steal it. */
final class BodyguardTargetGoal extends Goal {
    private final Human human;
    BodyguardTargetGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.TARGET));
    }
    @Override public boolean canUse() {
        return BodyguardProtection.isBodyguard(human) && BodyguardProtection.owner(human) != null
                && !human.isNoAi() && !human.isSleeping() && !human.isFleeing
                && !SoldierOrder.isReturningFromRetreat(human);
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { tick(); }
    @Override public void tick() {
        BodyguardProtection.update(human);
        var desired = BodyguardProtection.attack(human);
        if (desired != null && !human.canAttack(desired)) desired = null;
        if (human.getTarget() != desired) {
            // A tactical priority switch is not a failed fight: do not blacklist
            // the old opponent or erase the last self-attacker / recovery use.
            human.setTarget(desired);
        }
    }
    @Override public void stop() { BodyguardProtection.clear(human); }
}
