package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Constant-time route checks. Actual path searches still use the shared budget. */
public final class RetreatRouteContinuity {
    private RetreatRouteContinuity() {}

    public static boolean needsExtension(Human human, LivingEntity threat) {
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone() || human.getNavigation().isStuck()) return true;
        Vec3 end = Vec3.atBottomCenterOf(path.getTarget());
        // Start planning before arrival; allow extra lead time at sprint speed.
        double speed = human.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (threat == null) return shouldExtend(human.position().distanceToSqr(end), speed,
                Double.POSITIVE_INFINITY, 1.0D);
        Vec3 away = NavigationSupport.horizontalDirection(threat.position(), human.position());
        Vec3 next = NavigationSupport.horizontalDirection(human.position(), path.getNextEntityPos(human));
        // A pursuer coming around us can invalidate a route that was safe earlier.
        double dot = away.lengthSqr() > 0.01D && next.lengthSqr() > 0.01D ? next.dot(away) : 1.0D;
        return shouldExtend(human.position().distanceToSqr(end), speed, human.distanceToSqr(threat), dot);
    }

    static boolean shouldExtend(double endDistanceSqr, double speed, double threatDistanceSqr, double dot) {
        double lead = Math.max(4.0D, Math.min(8.0D, speed * 20));
        return endDistanceSqr <= lead * lead || (threatDistanceSqr <= 144.0D && dot < -0.25D);
    }

    public static int claimSearch(Human human, int maximum) {
        return OptionalPathBudget.claim(human, OptionalPathBudget.Kind.RETREAT, maximum);
    }

    public static void cancelSearch(Human human) {
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.RETREAT);
    }
}
