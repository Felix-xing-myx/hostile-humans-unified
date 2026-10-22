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
        if (human.getTarget() == null) MovementSpeedController.normal(human);
    }
}
