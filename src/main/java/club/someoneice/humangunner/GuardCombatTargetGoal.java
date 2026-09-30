package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Prevent vanilla follow-range target goals from erasing an authorized guard fight. */
final class GuardCombatTargetGoal extends Goal {
    private final Human human;
    private LivingEntity startingTarget;
    GuardCombatTargetGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.TARGET));
    }
    @Override public boolean canUse() {
        startingTarget = null;
        if (!human.hasOwner() || SoldierOrder.get(human) != SoldierOrder.GUARD
                || human.guardCombatState.returning()) return false;
        LivingEntity current = human.getTarget();
        if (current != null && current.isAlive() && human.canAttack(current)) {
            startingTarget = current;
            human.guardCombatState.retainTarget(current.getUUID());
            return true;
        }
        var id = human.guardCombatState.attacker(human.level().getGameTime());
        return id != null && human.level() instanceof ServerLevel level
                && level.getEntity(id) instanceof LivingEntity attacker && human.canAttack(attacker);
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void tick() {
        LivingEntity current = human.getTarget();
        if (current != null && current.isAlive() && human.canAttack(current)) return;
        var id = human.guardCombatState.attacker(human.level().getGameTime());
        if (id != null && human.level() instanceof ServerLevel level
                && level.getEntity(id) instanceof LivingEntity attacker && human.canAttack(attacker)) {
            human.setTarget(attacker);
        }
    }
    @Override public void start() {
        // Stopping the previous vanilla target goal may clear Mob#getTarget.
        // Restore the accepted fight without running a new nearest-target scan.
        if (startingTarget != null && startingTarget.isAlive() && human.canAttack(startingTarget))
            human.setTarget(startingTarget);
        startingTarget = null;
        tick();
    }
    @Override public void stop() {
        startingTarget = null;
        human.guardCombatState.retainTarget(null);
    }
    // Do not clear a target on stop: nearby tactical switching owns that target.
}
