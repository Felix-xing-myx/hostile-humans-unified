package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.navigation.HumanNavigation;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.soldier.NaturalRoute;
import dev.felix.hostilehumans.soldier.OrderDistances;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import dev.felix.hostilehumans.navigation.GuardReturnProgress;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Bounded return-route execution. SoldierOrder alone decides permissions and completes the order. */
public final class GarrisonReturnNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final double[] ANGLES = {0, Math.PI/8, -Math.PI/8, Math.PI/4, -Math.PI/4, Math.PI/2, -Math.PI/2, Math.PI};
    private static final class State {
        final GuardReturnProgress progress = new GuardReturnProgress();
        final OrderDistances.Context context;
        final Vec3 preferred;
        Path path;
        Vec3 point;
        int candidate, failures;
        long retryAt;
        boolean reportedFailure;
        String reason = "准备返岗";
        State(OrderDistances.Context context, Vec3 preferred) { this.context = context; this.preferred = preferred; }
    }
    private GarrisonReturnNavigation() {}
    public static void clear(Human human) { stopOwned(human); STATES.remove(human); }
    public static void stopOwned(Human human) {
        State state = STATES.get(human);
        if (state == null) return;
        if (MovementContinuity.ownsRoute(human, state.path)) human.getNavigation().stop();
        state.path = null; state.progress.pause();
    }
    public static String status(Human human) {
        State state = STATES.get(human);
        if (!human.guardCombatState.returning()) return "未返岗";
        return SoldierOrder.yieldsReturnToDefense(human) ? "暂停返岗，原地反击已确认攻击者"
                : state == null ? "准备返岗" : state.reason;
    }
    public static boolean tick(Human human, OrderDistances.Context context, Vec3 preferred) {
        State state = STATES.get(human);
        if (state == null || !state.context.equals(context) || !state.preferred.equals(preferred)) {
            clear(human); state = new State(context, preferred); STATES.put(human, state);
        }
        long now = human.level().getGameTime();
        double reach = context.settings().stay() ? OrderDistances.defaults().returnDistance()
                : OrderDistances.defaults().routeReachDistance();
        if (NaturalRoute.reached(human, preferred, reach)
                || state.point != null && NaturalRoute.reached(human, state.point, reach)) return true;
        // Free patrol has returned once safely inside its original area; fixed posts/routes keep their endpoint.
        if (!context.settings().stay() && !NaturalRoute.hasRoute(human, context) && human.onGround()
                && context.horizontalDistanceSquared(human.position()) <= Math.pow(Math.max(0, context.dailyRadius()-1.5), 2)
                && human.tickCount % 10 == 0) {
            Vec3 landing = SafePositions.nearPost(human, human.blockPosition(), human.getBlockX(), human.getBlockZ());
            if (landing != null && NaturalRoute.reached(human, landing, reach)) return true;
        }
        if (state.path != null && MovementContinuity.ownsRoute(human, state.path)) {
            Path live = human.getNavigation().getPath();
            if (live != null && !live.isDone() && !state.progress.stalled(now, live.getNextNodeIndex(),
                    human.position().distanceToSqr(live.getNextEntityPos(human)))) {
                state.reason = "执行返岗路线"; return false;
            }
            fail(human, state, now, "返岗路线结束过早或无进展");
        } else if (state.path != null) {
            // A survival/continuity controller replaced the route. Reacquire only on this Goal's next tick.
            state.path = null; state.progress.pause(); state.reason = "返岗路线控制权变化"; return false;
        }
        if (now < state.retryAt && state.retryAt <= now + OrderDistances.defaults().routeFailureBackoffTicks()) {
            state.reason = "返岗失败退避，等待重试"; return false;
        }
        if (state.point == null) state.point = candidate(human, context, preferred, state.candidate, reach);
        if (state.point == null) { fail(human, state, now, "返岗候选净空或范围不符"); return false; }
        if (!BasicNavigationBudget.tryAcquire(human, 1)) { state.reason = "返岗等待寻路预算"; return false; }
        try {
            BlockPos goal = BlockPos.containing(state.point);
            Path path = human.getNavigation() instanceof HumanNavigation navigation
                    ? navigation.createFreshPath(goal) : human.getNavigation().createPath(goal, 0);
            if (SoldierOrder.installPostPath(human, path, goal, OrderDistances.defaults().walkSpeed(), true)) {
                state.path = human.getNavigation().getPath(); state.progress.start();
                state.reason = state.candidate == 0 ? "执行原驻点返岗路线" : "执行驻区内替代返岗路线";
                NavigationDebugService.candidate(human, Vec3.atBottomCenterOf(goal), "返岗", state.reason, state.candidate);
            } else fail(human, state, now, "返岗路径不完整或被安全规则拒绝");
        } finally { BasicNavigationBudget.cancel(human); }
        return false;
    }
    private static Vec3 candidate(Human human, OrderDistances.Context context, Vec3 preferred, int index, double reach) {
        if (index == 0) return human.level().hasChunkAt(BlockPos.containing(preferred)) ? preferred : null;
        boolean fixed = context.settings().stay() || NaturalRoute.hasRoute(human, context);
        double angle = fixed ? (index-1) * Math.PI/4
                : Math.atan2(human.getZ()-context.origin().z, human.getX()-context.origin().x) + ANGLES[(index-1)%8];
        double radius = fixed ? 1 : Math.min(Math.sqrt(context.horizontalDistanceSquared(human.position())),
                Math.max(0, context.dailyRadius()-2));
        Vec3 center = fixed ? preferred : context.origin();
        Vec3 point = SafePositions.nearPost(human, BlockPos.containing(center),
                (int)Math.floor(center.x + Math.cos(angle)*radius), (int)Math.floor(center.z + Math.sin(angle)*radius));
        if (point == null || !context.insideDaily(point) || fixed && !NaturalRoute.reached(point, preferred, reach)) return null;
        if (fixed && human.level().clip(new net.minecraft.world.level.ClipContext(point.add(0,.4,0), preferred.add(0,.4,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, human))
                .getType() != net.minecraft.world.phys.HitResult.Type.MISS) return null;
        return point;
    }
    private static void fail(Human human, State state, long now, String reason) {
        stopOwned(human); state.point = null; state.reason = reason;
        state.candidate = (state.candidate+1)%9;
        if (GuardReturnProgress.backoff(++state.failures)) {
            state.failures = 0; state.retryAt = now + OrderDistances.defaults().routeFailureBackoffTicks();
            if (!state.reportedFailure) {
                HumanRuntime.LOGGER.warn("Garrison return retry: npc={} position={} post={} reason={} retryTicks={}",
                        human.getUUID(), human.position(), state.preferred, reason, OrderDistances.defaults().routeFailureBackoffTicks());
                state.reportedFailure = true;
            }
        }
        NavigationDebugService.candidate(human, state.preferred, "返岗", reason, state.candidate);
    }
}
