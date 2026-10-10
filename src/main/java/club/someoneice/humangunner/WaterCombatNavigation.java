package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Water pursuit watchdog. Reuses the existing shore planner and lease, never a second MOVE goal. */
public final class WaterCombatNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID target;
        Vec3 anchor;
        int progressTick, nextSearch;
        BlockPos avoid;
        Path route;
        final ShoreProgressTracker progress = new ShoreProgressTracker();
    }
    private WaterCombatNavigation() { }

    public static boolean ownsRoute(Human human) {
        State state = STATES.get(human);
        return state != null && state.route != null && human.isSeekingShore()
                && MovementContinuity.ownsRoute(human, state.route);
    }

    /** Called from ordinary water approach; true reserves movement for a reachable dry exit. */
    public static boolean update(Human human, LivingEntity target, double maximumRange, double speed) {
        if (human.level().isClientSide || target == null || !target.isAlive() || !human.canAttack(target)
                || !human.isInWater() || human.isFleeing || SoldierOrder.isHoldingPosition(human)) return false;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (!target.getUUID().equals(state.target)) {
            release(human, state);
            state.target = target.getUUID(); state.anchor = human.position();
            state.progressTick = human.tickCount; state.nextSearch = human.tickCount;
            state.avoid = null;
        }
        if (ownsRoute(human)) return true;
        if (human.isSeekingShore()) return false; // Another shore owner retains its lease.
        if (human.distanceToSqr(target) <= maximumRange * maximumRange && human.getSensing().hasLineOfSight(target)) {
            state.anchor = human.position(); state.progressTick = human.tickCount;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
            return false; // A stationary unit already able to attack is not stuck.
        }
        // Ignore vertical bobbing. Repath success alone is not physical progress.
        if (human.position().subtract(state.anchor).horizontalDistanceSqr() >= .25) {
            state.anchor = human.position(); state.progressTick = human.tickCount;
        }
        if (human.tickCount - state.progressTick < CombatTerrainPolicy.STALL_TICKS
                || human.tickCount < state.nextSearch) return false;
        if (OptionalPathBudget.claimFull(human, OptionalPathBudget.Kind.WATER_PURSUIT, 4) != 4) return false;
        Path path = human.findNearestShorePath(state.avoid);
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
        state.nextSearch = human.tickCount + 40;
        if (path == null || !path.canReach() || !HumanTerrainSafety.safePath(human, path)
                || !allowed(human, path) || !human.startCombatShoreDetour(path, speed)) return false;
        state.route = human.getNavigation().getPath();
        state.progress.reset(human.tickCount);
        human.getPersistentData().putString("hostile_humans_unified:combat_terrain_state", "WATER_DETOUR");
        return true;
    }

    private static boolean allowed(Human human, Path path) {
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (!SoldierOrder.allowsCombatPosition(human, new Vec3(node.x + .5, node.y, node.z + .5))) return false;
        }
        return true;
    }

    /** Validate even when a weapon goal yields movement or the target is no longer valid. */
    public static void tick(Human human) {
        if (human.level().isClientSide) return;
        State state = STATES.get(human);
        if (state == null) return;
        LivingEntity target = human.getTarget();
        if (target == null || !target.isAlive() || !target.getUUID().equals(state.target)
                || target.level() != human.level() || !human.canAttack(target) || !human.isAlive()
                || !human.isInWater() || human.isFleeing || human.isNoAi() || human.isSleeping()
                || human.isPassenger() || SoldierOrder.isHoldingPosition(human)) {
            release(human, state); STATES.remove(human); return;
        }
        if (state.route == null) return;
        Path live = human.getNavigation().getPath();
        boolean owned = ownsRoute(human);
        if (owned && live != null && !live.isDone()) {
            Vec3 next = live.getNextEntityPos(human);
            state.progress.observe(human.tickCount, live.getNextNodeIndex(),
                    next.subtract(human.position()).horizontalDistance());
            if (!state.progress.isStalled(human.tickCount)) return;
            if (human.getNavigation() instanceof HumanNavigation ground)
                ground.avoidWaypoint(live.getNextNodePos(), human.level().getGameTime() + 100);
        }
        state.avoid = state.route.getTarget().immutable();
        release(human, state);
        state.nextSearch = human.tickCount + 10;
        state.progressTick = human.tickCount - CombatTerrainPolicy.STALL_TICKS;
        state.anchor = human.position();
    }

    private static void release(Human human, State state) {
        if (ownsRoute(human)) human.stopSeekingShore();
        state.route = null;
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
    }
}
