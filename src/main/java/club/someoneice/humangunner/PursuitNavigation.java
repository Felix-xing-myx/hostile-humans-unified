package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Refresh pursuit before an obsolete endpoint, retaining safe routes on budget waits or failure. */
public final class PursuitNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID target;
        Vec3 sampled;
        int attempt;
        boolean failed;
    }
    private PursuitNavigation() {}

    public static boolean update(Human human, LivingEntity target, double speed) {
        var nav = human.getNavigation();
        Path previous = nav.getPath();
        boolean hasRoute = previous != null && !previous.isDone();
        if (target == null || !target.isAlive() || !human.canAttack(target)
                || human.isFleeing || SoldierOrder.isHoldingPosition(human)) return hasRoute;
        State state = STATES.get(human);
        if (state == null || !target.getUUID().equals(state.target) || human.tickCount < state.attempt) {
            state = new State(); state.target = target.getUUID(); state.attempt = human.tickCount - 4;
            state.sampled = hasRoute ? Vec3.atBottomCenterOf(previous.getTarget()) : target.position();
            STATES.put(human, state);
        }
        boolean nearEnd = hasRoute && previous.getNodeCount() - previous.getNextNodeIndex() <= 2;
        if (!PursuitRefreshPolicy.due(human.tickCount - state.attempt, hasRoute, nearEnd,
                target.position().distanceToSqr(state.sampled), state.failed)) return hasRoute;
        // Deferred budget is live work; retain the route and rejoin the FIFO next tick.
        if (!BasicNavigationBudget.tryAcquire(human, 1)) return hasRoute;
        state.attempt = human.tickCount;
        state.sampled = target.position();
        Path replacement = nav instanceof HumanNavigation ground
                ? ground.createFreshPath(target, 0) : nav.createPath(target, 0);
        boolean useful = replacement != null && !replacement.isDone()
                && HumanTerrainSafety.safePath(human, replacement)
                && (replacement.canReach() || Vec3.atBottomCenterOf(replacement.getTarget())
                        .distanceToSqr(human.position()) > 1.0D);
        if (useful && nav.moveTo(replacement, speed)) { state.failed = false; return true; }
        state.failed = true;
        // Never install null: ordinary moveTo(Entity) would discard its old path on failure.
        if (hasRoute && nav.getPath() != previous) nav.moveTo(previous, speed);
        return nav.getPath() != null && !nav.isDone();
    }
}
