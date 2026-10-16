package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Anonymous arrow reaction uses the existing shield controller; never acquires a living target. */
final class ProjectileReactionGoal extends Goal {
    private final Human human;
    private final ShieldDefenseController shields;
    private ShieldDefenseController.IncomingArrow incoming;
    ProjectileReactionGoal(Human human) {
        this.human = human;
        this.shields = human.humanGunner$shieldDefense();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        if (!shields.allowsProjectileReaction() || HumanPerception.canTarget(human, human.getTarget())) return false;
        incoming = shields.findIncomingArrow();
        return incoming != null;
    }
    @Override public void start() {
        if (!shields.beginProjectileReaction(incoming)) return;
        human.getNavigation().stop();
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        human.getMoveControl().setWantedPosition(human.getX(), human.getY(), human.getZ(), 0);
    }
    @Override public boolean canContinueToUse() { return shields.continuesProjectileReaction(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { shields.tickProjectileReaction(); }
    @Override public void stop() {
        shields.endProjectileReaction();
        incoming = null;
        if (human.getTarget() == null) MovementSpeedController.normal(human);
    }
}
