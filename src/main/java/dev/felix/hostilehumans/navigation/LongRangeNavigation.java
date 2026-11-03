package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.navigation.HumanNavigation;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.performance.FairWorkBudget;
import dev.felix.hostilehumans.performance.BudgetDemandOrder;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import dev.felix.hostilehumans.soldier.OrderDistances;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import dev.felix.hostilehumans.navigation.RouteSearchPolicy;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** One explicit-route seam. Queries never install paths or acquire MOVE/LOOK ownership. */
public final class LongRangeNavigation {
    public enum Purpose { COMBAT, LOOT, FOLLOW, INVESTIGATION, TEST }
    public enum Status { READY, PENDING, FAILED, RESTRICTED, UNSUPPORTED }
    public record Result(Status status, Path path) {
        public boolean pending() { return status == Status.PENDING; }
    }
    private static final Result PENDING = new Result(Status.PENDING,null);
    private static final Result FAILED = new Result(Status.FAILED,null);
    private static final class Actor {
        final EnumMap<Purpose,State> lanes = new EnumMap<>(Purpose.class);
        State latest;
        Purpose remoteWaiter;
    }
    private static final class State {
        BlockPos goal;
        Path route;
        int stage, lastRange, lastNodes, nextTick, queriedTick = Integer.MIN_VALUE, queuedAt = -1, expanded, searches, reuses, signature;
        String result = "new";
        final LinkedHashMap<BlockPos,Failure> failures = new LinkedHashMap<>();
    }
    private record Failure(Vec3 origin, BlockPos probe, int tick, int attempts, int scene, int signature, boolean restricted) {}
    private static final class Pool {
        private int tick;
        private NavigationSearchConfig settings;
        private FairWorkBudget<UUID> queue;

        NavigationSearchConfig settingsFor(int now) {
            if (settings == null || now != tick) {
                NavigationSearchConfig next = config();
                if (settings == null || next.remoteUnits() != settings.remoteUnits()) {
                    int units = next.remoteUnits();
                    queue = new FairWorkBudget<>(units, units, 40);
                }
                settings = next;
                tick = now;
            }
            return settings;
        }
    }
    public static String usage(MinecraftServer server) {
        var pool = POOLS.get(server);
        var settings = pool != null && pool.tick == server.getTickCount() ? pool.settings : config();
        int cap = AdaptiveActivityBudget.navigationCapacity(server, settings.remoteUnits(),
                RouteSearchPolicy.remoteCost(settings.longNodes()));
        return pool == null ? "0 / " + cap : pool.queue.usage(server.getTickCount(),cap);
    }
    private static final Map<Human,Actor> ACTORS = new WeakHashMap<>();
    private static final Map<MinecraftServer,Pool> POOLS = new WeakHashMap<>();
    private LongRangeNavigation() {}
    private static NavigationSearchConfig config() { return UnifiedConfig.get().navigationSearch(); }

    public static Result search(Human human, BlockPos goal, Purpose purpose, int accuracy) {
        if (!(human.getNavigation() instanceof HumanNavigation nav)) return new Result(Status.UNSUPPORTED,null);
        if (human.level().isClientSide || !human.isAlive() || human.isNoAi() || goal == null) return FAILED;
        MinecraftServer server = human.level().getServer();
        if (server == null) return FAILED;
        Pool pool = POOLS.computeIfAbsent(server, ignored -> new Pool());
        var c = pool.settingsFor(server.getTickCount());
        int tick = human.tickCount;
        Actor actor = ACTORS.computeIfAbsent(human,k -> new Actor());
        State s = actor.lanes.computeIfAbsent(purpose,k -> new State());actor.latest = s;
        int signature = signature(human);
        boolean newRequest=s.goal==null||s.signature!=signature||tick<s.queriedTick;
        boolean changedGoal=newRequest||s.goal.distSqr(goal)>c.retargetDistance()*c.retargetDistance();
        if (changedGoal) {
            boolean wasPending=!newRequest&&s.queuedAt>=0;
            s.goal=goal.immutable();
            int initial=c.enabled()?RouteSearchPolicy.startStage(Math.hypot(human.getX()-goal.getX(),human.getZ()-goal.getZ()),c.normalRange(),c.extendedRange()):0;
            s.stage=wasPending?Math.max(s.stage,initial):initial;s.route=null;s.nextTick=tick;
            if(!wasPending)s.queuedAt=-1;
            s.signature=signature;
        }
        Path current = nav.getPath();
        if (s.route != null && current != null && !current.isDone() && !nav.isStuck() && !nav.needsBoundedRefresh()
                && (current == s.route || MovementContinuity.ownsRoute(human,s.route))) {
            if (tick < s.nextTick || valid(human,current,purpose)) {
                if (tick >= s.nextTick) s.nextTick = tick+c.validationTicks();
                s.route = current;s.reuses++;s.result = "复用";
                return new Result(Status.READY,current);
            }
            s.route = null; // Caller still owns the installed path; this query never stops it.
        }
        // A completed, exhausted or externally replaced route starts a new bounded attempt, not a stale Path reuse.
        if (s.route != null) { s.route = null;s.stage = 0;s.nextTick = tick; }
        Failure failure = nearbyFailure(s,goal);
        if (failure != null && failure.signature == signature) {
            int elapsed = tick-failure.tick;
            boolean moved = failure.origin.distanceToSqr(human.position()) > 16;
            int delay = RouteSearchPolicy.failureDelay(failure.attempts,c.failureTicks(),c.maximumFailureTicks());
            boolean changed = elapsed >= delay && scene(human,goal,failure.origin,failure.probe) != failure.scene;
            if (!RouteSearchPolicy.retry(elapsed,delay,c.maximumFailureTicks(),moved,changed)) {
                cancelQueue(human, actor, purpose);s.queuedAt=-1;s.result = "失败缓存";return failure.restricted?new Result(Status.RESTRICTED,null):FAILED;
            }
        }
        if (tick < s.nextTick || s.queriedTick == tick) return pending(s,tick,"分档等待");
        if (!human.level().hasChunkAt(goal) || human.level().isOutsideBuildHeight(goal)
                || !human.level().getWorldBorder().isWithinBounds(goal)) { s.result = "目标未加载或越界";s.queuedAt=-1;cancelQueue(human, actor, purpose);return FAILED; }
        if(!nav.canRunBoundedSearch())return pending(s,tick,"等待落地或浮水");
        int stage = c.enabled() ? s.stage : 0;
        int range = c.range(stage), nodes = c.nodes(stage);
        if (stage == 0) {
            cancelQueue(human, actor, purpose);
            if (!BasicNavigationBudget.tryAcquire(human,1)) return pending(s,tick,"普通配额等待");
            BasicNavigationBudget.cancel(human);
        } else {
            pool.queue.setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,c.remoteUnits(),RouteSearchPolicy.remoteCost(c.longNodes())));
            int cost = RouteSearchPolicy.remoteCost(nodes);
            if (pool.queue.claimFull(server.getTickCount(),human.getUUID(),cost,BudgetDemandOrder.faction(human)) != cost) {
                actor.remoteWaiter = purpose;
                return pending(s,tick,"远程配额等待");
            }
            actor.remoteWaiter = null;
            pool.queue.cancel(human.getUUID()); // No speculative reservation after consumed work.
        }
        s.queriedTick = tick;s.queuedAt = -1;s.searches++;s.goal=goal.immutable();s.lastRange=range;s.lastNodes=nodes;
        Path path = nav.createBoundedPath(goal,accuracy,range,nodes);s.expanded = nav.expandedNodes();
        if (valid(human,path,purpose)) {
            s.route = path;s.nextTick = tick+c.validationTicks();s.result = "完整";
            s.failures.entrySet().removeIf(e -> e.getKey().distSqr(goal) <= 4);
            return new Result(Status.READY,path);
        }
        // A complete route rejected by safety/command policy must not trigger more expensive searches.
        if (c.enabled() && (path == null || !path.canReach()) && stage < 2) {
            s.stage = stage+1;s.nextTick = tick+1;
            return pending(s,tick,"扩大搜索");
        }
        int attempts = failure == null || failure.signature != signature
                || failure.origin.distanceToSqr(human.position()) > 16 ? 1 : Math.min(7,failure.attempts+1);
        BlockPos probe = path != null && path.getEndNode() != null ? path.getEndNode().asBlockPos() : goal;
        s.failures.entrySet().removeIf(e -> e.getKey().distSqr(goal) <= 4);
        while(s.failures.size()>=c.cacheEntries())s.failures.remove(s.failures.keySet().iterator().next());
        s.failures.put(goal.immutable(),new Failure(human.position(),probe.immutable(),tick,attempts,
                scene(human,goal,human.position(),probe),signature,path!=null&&path.canReach()));
        s.nextTick=tick+1;s.result=path!=null&&path.canReach()?"安全或命令拒绝":"未找到完整路";
        cancelQueue(human, actor, purpose);return path!=null&&path.canReach()?new Result(Status.RESTRICTED,null):FAILED;
    }
    private static Result pending(State s,int tick,String reason) {
        if(s.queuedAt<0)s.queuedAt=tick;s.result=reason;return PENDING;
    }
    private static Failure nearbyFailure(State s,BlockPos goal) {
        Failure exact=s.failures.get(goal);if(exact!=null)return exact;
        for(var entry:s.failures.entrySet())if(entry.getKey().distSqr(goal)<=4)return entry.getValue();
        return null;
    }
    private static int signature(Human h) {
        // Real command changes cancel all lanes through SoldierOrder; health changes invalidate reuse immediately.
        return Objects.hash(OrderDistances.signature(h),h.getOwnerUUID(),h.getMaxFallDistance(),h.isFleeing,
                h.getNavigation().canFloat());
    }
    private static boolean valid(Human h,Path path,Purpose purpose) {
        if(path==null||!path.canReach()||path.isDone()||!HumanTerrainSafety.safePath(h,path))return false;
        if(purpose==Purpose.TEST||purpose==Purpose.FOLLOW)return true;
        if(purpose==Purpose.INVESTIGATION)return SoldierOrder.allowsDailyPath(h,path);
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++) {
            var node=path.getNode(i);
            if(purpose==Purpose.LOOT?!SoldierOrder.allowsLootPosition(h,node.x+.5,node.z+.5)
                    :!SoldierOrder.allowsCombatPosition(h,new Vec3(node.x+.5,node.y,node.z+.5)))return false;
        }
        return true;
    }
    private static int scene(Human h,BlockPos goal,Vec3 origin,BlockPos probe) {
        int hash=1;
        for(BlockPos point:new BlockPos[]{goal,BlockPos.containing(origin),probe})
            for(BlockPos cell:new BlockPos[]{point,point.above(),point.below(),point.north(),point.south(),point.east(),point.west()})
                hash=31*hash+(h.level().hasChunkAt(cell)?h.level().getBlockState(cell).hashCode():0);
        return hash;
    }
    private static void cancelQueue(Human h) {
        Pool pool=POOLS.get(h.level().getServer());if(pool!=null)pool.queue.cancel(h.getUUID());
    }
    private static void cancelQueue(Human human, Actor actor, Purpose purpose) {
        if (actor.remoteWaiter != purpose) return;
        actor.remoteWaiter = null;
        cancelQueue(human);
    }
    public static void cancel(Human human, Purpose purpose) {
        Actor actor = ACTORS.get(human);
        if (actor == null) return;
        State state = actor.lanes.remove(purpose);
        if (actor.latest == state) actor.latest = null;
        cancelQueue(human, actor, purpose);
    }
    public static void accepted(Human h,Purpose purpose) {
        Actor actor=ACTORS.get(h);State s=actor==null?null:actor.lanes.get(purpose);
        if(s!=null&&s.route!=null)s.route=h.getNavigation().getPath();
    }
    public static boolean inProgress(Human h,Purpose purpose) {
        Actor actor=ACTORS.get(h);State s=actor==null?null:actor.lanes.get(purpose);
        return s!=null&&s.queuedAt>=0;
    }
    public static void cancelAll(Human h) { ACTORS.remove(h);cancelQueue(h); }
    /** Fits the existing bounded debug string, so no protocol bump or client-side authority is required. */
    public static String describe(Human h) {
        Actor actor=ACTORS.get(h);State s=actor==null?null:actor.latest;if(s==null)return "";
        int stage=Math.min(2,s.stage);
        return "档"+stage+" R"+s.lastRange+" 展开"+s.expanded+"/"+s.lastNodes+" "+s.result
                +" 等"+(s.queuedAt<0?0:Math.max(0,h.tickCount-s.queuedAt))+"t 算"+s.searches+" 复用"+s.reuses;
    }
}
