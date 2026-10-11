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
        Path route;
    }
    private PursuitNavigation() {}

    public static boolean update(Human human, LivingEntity target, double speed) {
        if (WaterCombatNavigation.isMeleePursuit(human, target) && !SoldierOrder.isHoldingPosition(human)) {
            human.approachCombatTargetInWater(target, MeleeCombatRange.reach(human, target), speed);
            if (human.hasWaterCombatMovementInput()) return true;
        }
        var nav = human.getNavigation();
        if (human.isSeekingShore() || human.isShoreTransitionPending()) return false;
        if (target == null || !target.isAlive() || !human.canAttack(target)
                || human.isFleeing || SoldierOrder.isHoldingPosition(human)) {
            clear(human);
            return false;
        }
        State state = STATES.get(human);
        if (state == null || !target.getUUID().equals(state.target) || human.tickCount < state.attempt) {
            if (state != null) clearOrdinaryRoute(human);
            state = new State(); state.target = target.getUUID(); state.attempt = human.tickCount - 4;
            state.sampled = target.position();
            STATES.put(human, state);
        }
        if (MeleeCombatRange.canStrike(human, target)) {
            CombatTerrainNavigation.clear(human);
            return false;
        }
        var terrain = CombatTerrainNavigation.update(human, target, speed, false);
        if (terrain.ownsMovement()) return terrain == CombatTerrainNavigation.Decision.ROUTE;
        Path previous = nav.getPath();
        boolean hasRoute = state.route != null && MovementContinuity.ownsRoute(human, state.route)
                && previous != null && !previous.isDone();
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
                && CombatTerrainNavigation.usefulPath(human, target, replacement);
        if (useful && nav.moveTo(replacement, speed)) {
            state.route = nav.getPath(); state.failed = false;
            CombatTerrainNavigation.accepted(human, target);
            return true;
        }
        state.failed = true;
        CombatTerrainNavigation.failed(human, target);
        // Never install null: ordinary moveTo(Entity) would discard its old path on failure.
        if (hasRoute && nav.getPath() != previous) nav.moveTo(previous, speed);
        return nav.getPath() != null && !nav.isDone();
    }

    static void clear(Human human) {
        clearOrdinaryRoute(human);
        CombatTerrainNavigation.clear(human);
    }

    private static void clearOrdinaryRoute(Human human) {
        State state = STATES.remove(human);
        if (state != null && state.route != null && MovementContinuity.ownsRoute(human, state.route))
            human.getNavigation().stop();
    }
}
