package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.MovementContinuityProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Repairs movement without changing targets, soldier orders or item-use custody. */
public final class MovementContinuity {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        final MovementContinuityProgress progress = new MovementContinuityProgress();
        int failures, nextFullSearch, stepUntil;
        boolean pendingFullSearch;
        Vec3 step;
        Vec3 progressPosition;
        Path route;
        Path repairedFrom, repairedRoute;
    }
    private MovementContinuity() {}

    public static void tick(Human human) {
        if (human.level().isClientSide) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        Path path = human.getNavigation().getPath();
        boolean moving = path != null && !path.isDone() && !human.isNoAi()
                && human.isAlive() && !human.isSleeping() && !human.isPassenger()
                && (human.onGround() || human.isInWater());
        if (!moving) {
            state.progress.stalled(human.tickCount, human.getX(), human.getZ(), 0, false);
            if (state.route != null || !human.isFleeing) state.step = null;
            state.route = null;
            state.failures = 0;
            state.pendingFullSearch = false;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.CONTINUITY);
            return;
        }
        double minimum = Math.max(0.005D, Math.min(0.08D,
                human.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.5D));
        if (state.progressPosition == null
                || human.position().multiply(1, 0, 1).distanceToSqr(state.progressPosition) >= minimum * minimum) {
            state.progressPosition = human.position().multiply(1, 0, 1);
            state.failures = 0;
            state.pendingFullSearch = false;
        }
        boolean stalled = state.progress.stalled(human.tickCount, human.getX(), human.getZ(),
                human.getAttributeValue(Attributes.MOVEMENT_SPEED), true);
        if (stalled) {
            state.failures++;
            // A fall or knockback can leave an already-passed waypoint behind us.
            while (!path.isDone() && human.position().distanceToSqr(path.getNextEntityPos(human)) < 0.16D)
                path.advance();
            if (path.isDone()) return;
            Vec3 next = path.getNextEntityPos(human);
            human.getMoveControl().setWantedPosition(next.x, next.y, next.z, 1.0D);
            if (state.failures > 1 && !human.isInWater() && !human.isInLava()) {
                NavigationSupport.openBlockingPassage(human);
                Vec3 toward = next.subtract(human.position()).multiply(1, 0, 1).normalize();
                if (human.onGround() && NavigationSupport.hasOneBlockObstacleAhead(human, toward)) {
                    human.getJumpControl().jump();
                } else if (state.step == null || human.tickCount >= state.stepUntil) {
                    Vec3 side = new Vec3(-toward.z, 0, toward.x).scale(0.55D);
                    if ((human.getId() & 1) != 0) side = side.scale(-1);
                    if (!tryStep(human, state, path, side)) tryStep(human, state, path, side.scale(-1));
                }
            }
            if (state.failures >= 3 && human.tickCount >= state.nextFullSearch) state.pendingFullSearch = true;
        }
        if (!state.pendingFullSearch) return;
        if (OptionalPathBudget.claim(human, OptionalPathBudget.Kind.CONTINUITY, 1) == 0) return;
        // Failed replacements never discard a route or its destination.
        BlockPos goal = path.getTarget();
        if (human.horizontalCollision && human.getNavigation()
                instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground
                && !path.getNextNodePos().equals(human.blockPosition())) {
            ground.avoidWaypoint(path.getNextNodePos(), human.level().getGameTime() + 40);
        }
        Path replacement = human.getNavigation()
                instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground
                ? ground.createFreshPath(goal) : human.getNavigation().createPath(goal, 0);
        boolean success = replacement != null && replacement != path && replacement.canReach()
                && pickupRouteAllowed(human, replacement)
                && human.getNavigation().moveTo(replacement, 1.0D);
        state.nextFullSearch = human.tickCount + (success ? 20 : Math.min(80, 20 + state.failures * 5));
        state.pendingFullSearch = false;
        if (success) {
            state.repairedFrom = state.repairedRoute == path ? state.repairedFrom : path;
            state.repairedRoute = replacement;
            state.step = null;
            state.failures = 0;
        }
        else if (state.failures >= 6) human.markNavigationGoalAbandoned(goal);
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.CONTINUITY);
    }

    /** A repaired route still belongs to the goal that originally assigned it. */
    public static boolean ownsRoute(Human human, Path assigned) {
        Path current = human.getNavigation().getPath();
        if (assigned == null) return false;
        if (current == assigned) return true;
        State state = STATES.get(human);
        return state != null && state.repairedFrom == assigned && state.repairedRoute == current;
    }

    private static boolean tryStep(Human human, State state, Path route, Vec3 offset) {
        if (offset.lengthSqr() < 0.01D) return false;
        Vec3 candidate = human.position().add(offset);
        BlockPos feet = BlockPos.containing(candidate);
        if (!human.level().hasChunkAt(feet)
                || (human.isActivelyCollectingLoot() && !human.isFleeing
                && !SoldierOrder.allowsLootPosition(human, candidate.x, candidate.z))
                || !human.level().getFluidState(feet).isEmpty()
                || !human.level().getBlockState(feet.below()).isFaceSturdy(human.level(), feet.below(), Direction.UP)
                || !human.level().noCollision(human, human.getBoundingBox().move(offset))) return false;
        state.step = candidate;
        state.route = route;
        state.stepUntil = human.tickCount + 5;
        return true;
    }

    private static boolean pickupRouteAllowed(Human human, Path path) {
        if (!human.isActivelyCollectingLoot() || human.isFleeing) return true;
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (!SoldierOrder.allowsLootPosition(human, node.x + 0.5D, node.z + 0.5D)) return false;
        }
        return true;
    }

    /** Safe local escape while a bounded planner waits; never substitutes for a complete route. */
    public static void escapeWhilePlanning(Human human, net.minecraft.world.entity.LivingEntity threat) {
        if (!human.isFleeing || threat == null || !human.onGround() || human.isInWater() || human.isInLava()
                || !human.getNavigation().isDone()) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (state.step != null && human.tickCount < state.stepUntil) return;
        Vec3 away = human.position().subtract(threat.position()).multiply(1, 0, 1).normalize().scale(0.55D);
        if (!tryStep(human, state, null, away)) {
            Vec3 side = new Vec3(-away.z, 0, away.x);
            if (!tryStep(human, state, null, side)) tryStep(human, state, null, side.scale(-1));
        }
    }

    /** Called after navigation writes its input, before the movement controller consumes it. */
    public static void applyStep(Human human) {
        State state = STATES.get(human);
        if (state == null || state.step == null) return;
        boolean escapeStep = state.route == null && human.isFleeing && human.getNavigation().isDone();
        if (human.tickCount >= state.stepUntil || (!escapeStep
                && (state.route != human.getNavigation().getPath() || human.getNavigation().isDone()))
                || !human.isAlive() || human.isNoAi() || human.isSleeping()) {
            state.step = null;
            return;
        }
        human.getMoveControl().setWantedPosition(state.step.x, state.step.y, state.step.z, 1.0D);
    }
}
