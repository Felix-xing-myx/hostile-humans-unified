package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Path;
import java.util.Map;
import java.util.WeakHashMap;

/** One idle follow route, with distance hysteresis and bounded replanning. */
final class SoldierFollowNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        boolean chasing;
        int nextSearch;
        BlockPos goal;
    }
    private SoldierFollowNavigation() { }

    static boolean shouldChase(boolean chasing, double distanceSquared) {
        return distanceSquared > (chasing ? 2.5D * 2.5D : 4.0D * 4.0D);
    }

    static void clear(Human human) { STATES.remove(human); }

    static void tick(Human human, ServerPlayer owner) {
        if (human.isNoAi() || human.isSleeping() || human.isPassenger()) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        double distance = human.distanceToSqr(owner);
        boolean chase = shouldChase(state.chasing, distance);
        if (!chase) {
            if (state.chasing) human.getNavigation().stop();
            state.chasing = false;
            state.goal = null;
            MovementSpeedController.normal(human);
            return;
        }
        if (!state.chasing) state.nextSearch = human.tickCount;
        state.chasing = true;
        boolean sprint = distance > 6.0D * 6.0D;
        MovementSpeedController.combat(human, sprint);
        double speed = UnifiedConfig.get().followSpeed(TierAttributes.key(human), distance > 12.0D * 12.0D);
        human.getNavigation().setSpeedModifier(speed);
        if (human.tickCount < state.nextSearch) return;
        BlockPos goal = owner.blockPosition();
        Path existing = human.getNavigation().getPath();
        boolean routeEnded = existing == null || existing.isDone();
        boolean ownerMoved = state.goal == null || state.goal.distSqr(goal) >= 4.0D;
        if (!routeEnded && !ownerMoved) return; // Let the existing route finish; do not reset it each half-second.
        Path replacement = human.getNavigation().createPath(owner, 0);
        boolean accepted = replacement != null && human.getNavigation().moveTo(replacement, speed);
        state.nextSearch = human.tickCount + 5;
        if (accepted) state.goal = goal.immutable();
        // Failure leaves any existing partial route intact; ordinary continuity
        // recovery and the established 48-block safe teleport remain authoritative.
    }
}
