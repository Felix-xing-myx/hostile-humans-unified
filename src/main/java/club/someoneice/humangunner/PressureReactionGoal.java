package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Survival without a known enemy. Known fights retain the existing adaptive defense/movement. */
final class PressureReactionGoal extends Goal {
    private final Human human;
    private final ShieldDefenseController shields;
    PressureReactionGoal(Human human) {
        this.human = human;
        shields = human.humanGunner$shieldDefense();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        return !HumanPerception.canTarget(human, human.getTarget()) && shields.allowsPressureReaction();
    }
    @Override public void start() {
        if (!shields.beginPressureReaction()) return;
        human.getNavigation().stop();
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        human.getMoveControl().setWantedPosition(human.getX(), human.getY(), human.getZ(), 0);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canContinueToUse() {
        return !HumanPerception.canTarget(human, human.getTarget()) && shields.continuesPressureReaction();
    }
    @Override public void tick() { shields.tickPressureReaction(); }
    @Override public void stop() {
        shields.endPressureReaction();
        if (human.getTarget() == null) MovementSpeedController.normal(human);
    }
}
