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
        if (human.getTarget() == null) MovementSpeedController.normal(human);
    }
}
