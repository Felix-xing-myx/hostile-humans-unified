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
        return human.hasOwner() && !human.isFleeing && human.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Movement is processed once by the server lifecycle; this goal only
        // reserves idle movement so unrelated wandering cannot override orders.
    }
}
