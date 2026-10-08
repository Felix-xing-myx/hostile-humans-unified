package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Owner approach with bounded retries and progress-based safe rescue. */
final class SoldierFollowNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        boolean chasing;
        int nextSearch, failures, progressTick, node;
        double bestDistance;
        BlockPos goal;
        Path route, observed;
    }
    private SoldierFollowNavigation() {}
    static boolean shouldChase(boolean chasing, double distanceSquared) {
        return distanceSquared > (chasing ? 2.5D * 2.5D : 4.0D * 4.0D);
    }
    static int retryDelay(int failures) { return Math.min(40, 5 + Math.max(0, failures) * 5); }
    static boolean rescueDue(int elapsed, double distanceSquared) {
        return elapsed >= 200 && distanceSquared > 6.25;
    }
    static void clear(Human human) { STATES.remove(human); }
    static void stopOwned(Human human) {
        State state = STATES.remove(human);
        if (state != null && MovementContinuity.ownsRoute(human, state.route)) human.getNavigation().stop();
        BasicNavigationBudget.cancel(human);
    }
    /** Protection uses this monitor even while it owns its intercept path. */
    static boolean rescueIfStuck(Human human, ServerPlayer owner) {
        return rescueIfStuck(human, owner, 2.5);
    }
    static boolean rescueIfStuck(Human human, ServerPlayer owner, double acceptableDistance) {
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        double distance = human.distanceTo(owner);
        if (!state.chasing) {
            state.chasing = true;
            state.progressTick = human.tickCount;
            state.bestDistance = distance;
        }
        Path current = human.getNavigation().getPath();
        boolean nodeProgress = current != null && current == state.observed
                && current.getNextNodeIndex() > state.node;
        if (distance < state.bestDistance - 0.5 || nodeProgress || distance <= acceptableDistance) {
            state.bestDistance = distance;
            state.progressTick = human.tickCount;
            state.failures = 0;
        }
        state.observed = current;
        state.node = current == null ? 0 : current.getNextNodeIndex();
        return distance > acceptableDistance && (human.distanceToSqr(owner) > 48 * 48 || rescueDue(human.tickCount - state.progressTick,
                human.distanceToSqr(owner))) && SoldierOrder.teleportBesideOwner(human, owner);
    }
    static void tick(Human human, ServerPlayer owner) {
        if (human.isNoAi() || human.isSleeping() || human.isPassenger() || human.isFleeing
                || owner.level() != human.level()) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        double distance = human.distanceToSqr(owner);
        if (!shouldChase(state.chasing, distance)) {
            stopOwned(human);
            MovementSpeedController.normal(human);
            return;
        }
        if (rescueIfStuck(human, owner)) return;
        MovementSpeedController.combat(human, distance > 36);
        double speed = UnifiedConfig.get().followSpeed(TierAttributes.key(human), distance > 144);
        human.getNavigation().setSpeedModifier(speed);
        if (human.tickCount < state.nextSearch) return;
        BlockPos goal = owner.blockPosition();
        Path old = human.getNavigation().getPath();
        boolean ended = old == null || old.isDone();
        boolean moved = state.goal == null || state.goal.distSqr(goal) >= 4;
        boolean stalled = human.tickCount - state.progressTick >= 40;
        if (!ended && !moved && !stalled) return;
        if (!BasicNavigationBudget.tryAcquire(human, 3)) return;
        Path replacement = human.getNavigation().createPath(owner, 0);
        boolean accepted = usable(human, owner, replacement)
                && human.getNavigation().moveTo(replacement, speed);
        for (int attempt = 0; !accepted && attempt < 2; attempt++) {
            int side = attempt == 0 ? 1 : -1;
            Vec3 pos = SafePositions.nearOwner(human, owner, owner.getBlockX() + side * 2,
                    owner.getBlockZ() + (Math.floorMod(human.getId(), 3) - 1));
            if (pos == null) continue;
            replacement = human.getNavigation().createPath(BlockPos.containing(pos), 0);
            accepted = usable(human, owner, replacement) && human.getNavigation().moveTo(replacement, speed);
        }
        if (accepted) {
            state.goal = goal.immutable();
            state.route = replacement;
            state.nextSearch = human.tickCount + 10;
        } else {
            if (old != null && !old.isDone()) human.getNavigation().moveTo(old, speed);
            state.nextSearch = human.tickCount + retryDelay(++state.failures);
        }
        BasicNavigationBudget.cancel(human);
    }
    private static boolean usable(Human human, ServerPlayer owner, Path path) {
        if (path == null || path.isDone() || path.getEndNode() == null) return false;
        var end = path.getEndNode();
        return path.canReach() || owner.distanceToSqr(end.x + 0.5, end.y, end.z + 0.5)
                + 1 < human.distanceToSqr(owner);
    }
}
