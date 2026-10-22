package com.craftix.hostile_humans.entity.ai;

import com.craftix.hostile_humans.HumanUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.*;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.HumanTerrainSafety;
import club.someoneice.humangunner.CombatTerrainNavigation;
import java.util.ArrayDeque;
import java.util.Deque;

/** Human-only navigation. Other mobs' fence/door classification is untouched. */
public final class HumanNavigation extends GroundPathNavigation implements club.someoneice.humangunner.HumanNavigationOwnership.OwnedNavigation {
    private final club.someoneice.humangunner.HumanNavigationOwnership movementOwnership = new club.someoneice.humangunner.HumanNavigationOwnership();
    @Override public club.someoneice.humangunner.HumanNavigationOwnership movementOwnership() { return movementOwnership; }
    private record BlockedEdge(BlockPos from, BlockPos to, long until) {}
    private final Deque<BlockedEdge> blockedEdges = new ArrayDeque<>();
    private Path sampledPath;
    private net.minecraft.world.phys.Vec3 sampledPosition;
    private int sampledNode, sampledTick;
    private boolean sampledAirborne;
    private int expandedNodes;
    private HumanNodeEvaluator boundedEvaluator;
    private PathFinder boundedFinder;
    private Path trialBoundedPath, activeBoundedPath;
    private BlockPos trialBoundedGoal, activeBoundedGoal;
    private boolean boundedRefresh;
    public boolean canRunBoundedSearch() { return canUpdatePath(); }
    public int expandedNodes() { return expandedNodes; }
    public boolean needsBoundedRefresh() { return boundedRefresh && path == activeBoundedPath; }

    /** Isolated query: leaves the installed route, vanilla target and stuck timers untouched. */
    public Path createBoundedPath(BlockPos goal, int accuracy, float range, int nodes) {
        if (nodes < 1 || nodes > 16384 || !Float.isFinite(range) || range < 1 || range > 256)
            throw new IllegalArgumentException("invalid path limits");
        expandedNodes = 0;
        if (!canUpdatePath() || !level.hasChunkAt(goal) || !level.getWorldBorder().isWithinBounds(goal)
                || level.isOutsideBuildHeight(goal)) return null;
        // GroundPathNavigation's vertical normalization, within an already loaded column only.
        BlockPos normalized = goal;
        if (level.getBlockState(normalized).isAir()) {
            BlockPos below = normalized.below();
            while (below.getY() > level.getMinBuildHeight() && level.getBlockState(below).isAir()) below = below.below();
            if (below.getY() > level.getMinBuildHeight()) normalized = below.above();
            else {
                BlockPos above = normalized.above();
                while (above.getY() < level.getMaxBuildHeight() && level.getBlockState(above).isAir()) above = above.above();
                normalized = above;
            }
        }
        while (normalized.getY() < level.getMaxBuildHeight() && level.getBlockState(normalized).isSolid()) normalized = normalized.above();
        if (level.isOutsideBuildHeight(normalized)) return null;
        if (boundedFinder == null) {
            boundedEvaluator = new HumanNodeEvaluator();
            boundedFinder = new PathFinder(boundedEvaluator, 16384);
        }
        boundedEvaluator.setCanPassDoors(nodeEvaluator.canPassDoors());
        boundedEvaluator.setCanOpenDoors(nodeEvaluator.canOpenDoors());
        boundedEvaluator.setCanFloat(nodeEvaluator.canFloat());
        boundedEvaluator.setCanWalkOverFences(nodeEvaluator.canWalkOverFences());
        BlockPos start = mob.blockPosition();
        int radius = (int) Math.ceil(range) + 8;
        var region = new net.minecraft.world.level.PathNavigationRegion(level, start.offset(-radius,-radius,-radius), start.offset(radius,radius,radius));
        long started = System.nanoTime();
        Path result = boundedFinder.findPath(region, mob, java.util.Set.of(normalized), range, accuracy, nodes / 16384.0F);
        trialBoundedPath = result;
        trialBoundedGoal = normalized.immutable();
        if (mob instanceof Human human) club.someoneice.humangunner.NavigationDebugService.searched(human,goal,result,System.nanoTime()-started);
        return result;
    }

    @Override public BlockPos getTargetPos() {
        return path != null && path == activeBoundedPath ? activeBoundedGoal : super.getTargetPos();
    }
    @Override public void recomputePath() {
        if (mob instanceof Human human && movementOwnership != null) {
            Path before = path;
            movementOwnership.runInstalled(human, before, () -> {
                recomputeOwnedPath(); movementOwnership.recomputed(human, before, path);
            });
        } else recomputeOwnedPath();
    }
    private void recomputeOwnedPath() {
        // The owning controller schedules a fresh bounded query. Do not recompute toward vanilla's old private target.
        if (path != null && path == activeBoundedPath) {
            if (level.getGameTime()-timeLastRecompute > 20) {
                boundedRefresh = true;hasDelayedRecomputation = false;timeLastRecompute = level.getGameTime();
            } else hasDelayedRecomputation = true;
            return;
        }
        super.recomputePath();
    }
    @Override public void stop() {
        if (movementOwnership != null && mob instanceof Human human && !movementOwnership.mayStop(human, path)) return;
        discardRoute();
        if (movementOwnership != null) movementOwnership.stopped();
    }
    private void discardRoute() {
        super.stop();activeBoundedPath = null;activeBoundedGoal = null;boundedRefresh = false;hasDelayedRecomputation = false;
    }

    public HumanNavigation(Mob mob, Level level) { super(mob, level); }

    @Override protected Path createPath(java.util.Set<BlockPos> goals, int padding, boolean offset,
            int accuracy, float distance) {
        boolean observed = mob instanceof Human human && club.someoneice.humangunner.NavigationDebugService.watching(human);
        long started = observed ? System.nanoTime() : 0;
        Path result = super.createPath(goals, padding, offset, accuracy, distance);
        if (observed && !goals.isEmpty()) club.someoneice.humangunner.NavigationDebugService.searched(
                (Human)mob, goals.iterator().next(), result, System.nanoTime() - started);
        return result;
    }

    /** Bypass same-target path reuse without abandoning the current route on failure. */
    public Path createFreshPath(BlockPos goal) {
        Path previous = this.path;
        try {
            this.path = null;
            return createPath(goal, 0);
        } finally {
            this.path = previous;
        }
    }

    /** Moving entities may keep their block while the cached route still points behind them. */
    public Path createFreshPath(net.minecraft.world.entity.Entity target, int accuracy) {
        Path previous = this.path;
        try {
            this.path = null;
            return createPath(target, accuracy);
        } finally {
            this.path = previous;
        }
    }

    /** Exclude a waypoint that repeatedly traps this human from the next path search. */
    public void avoidWaypoint(BlockPos node, long untilTick) {
        avoidTransition(mob.blockPosition(), node, untilTick);
        // Floating walk nodes start at the surface, not at the entity's submerged feet.
        if (mob.isInWater()) {
            BlockPos surface = mob.blockPosition();
            for (int i = 0; i < 16 && level.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER); i++)
                surface = surface.above();
            avoidTransition(surface, node, untilTick);
        }
        if (path != null && !path.isDone() && path.getNextNodePos().equals(node) && path.getNextNodeIndex() > 0)
            avoidTransition(path.getNodePos(path.getNextNodeIndex() - 1), node, untilTick);
    }

    public void avoidTransition(BlockPos from, BlockPos to, long untilTick) {
        if (from.equals(to)) return;
        blockedEdges.removeIf(edge -> edge.until() <= level.getGameTime()
                || edge.from().equals(from) && edge.to().equals(to));
        while (blockedEdges.size() >= 12) blockedEdges.removeFirst();
        blockedEdges.addLast(new BlockedEdge(from.immutable(), to.immutable(), untilTick));
    }

    private boolean isAvoided(BlockPos from, BlockPos to) {
        if (blockedEdges == null) return false; // Navigation superclass construction precedes our fields.
        blockedEdges.removeIf(edge -> edge.until() <= level.getGameTime());
        for (BlockedEdge edge : blockedEdges)
            if (edge.from().equals(from) && edge.to().equals(to)) return true;
        return false;
    }

    @Override public boolean moveTo(Path path, double speed) {
        return mob instanceof Human human && movementOwnership != null
                ? movementOwnership.install(human, () -> this.path, () -> installRoute(path, speed), this::discardRoute)
                : installRoute(path, speed);
    }
    private boolean installRoute(Path path, double speed) {
        if (mob instanceof Human human && path != null && !path.isDone()) {
            var assessment = HumanTerrainSafety.assessPath(human, path);
            if (!assessment.safe()) { CombatTerrainNavigation.blocked(human, assessment); return false; }
        }
        Path previous = this.path;
        boolean sameRoute = previous != null && !previous.isDone() && path != null && !path.isDone() && path.sameAs(previous);
        boolean accepted;
        if (sameRoute) {
            // Reusing a live route must not restart the native 100-tick physical-stall watchdog.
            setSpeedModifier(speed);accepted = true;
        } else accepted = super.moveTo(path, speed);
        if (accepted && path != null) {
            if (path == trialBoundedPath) {
                activeBoundedPath = this.path; activeBoundedGoal = trialBoundedGoal; boundedRefresh = false;
                hasDelayedRecomputation = false;timeLastRecompute = level.getGameTime();
                if (this.path != previous) {
                    // Query isolation preserves the OLD route timer; acceptance starts the NEW route timer.
                    timeoutCachedNode = net.minecraft.core.Vec3i.ZERO;
                    timeoutTimer = 0;timeoutLimit = 0;lastTimeoutCheck = level.getGameTime();
                }
                // First acceptance consumes this query. Cache re-submission must preserve pending block refreshes.
                trialBoundedPath = null;trialBoundedGoal = null;
            } else if (this.path != activeBoundedPath) {
                activeBoundedPath = null; activeBoundedGoal = null; boundedRefresh = false;
            }
        }
        if (mob instanceof Human human && path != null)
            club.someoneice.humangunner.NavigationDebugService.candidate(human, net.minecraft.world.phys.Vec3.atBottomCenterOf(path.getTarget()),
                    "installed", accepted ? "accepted" : "rejected", Double.NaN);
        return accepted;
    }

    @Override public boolean isStuck() {
        // Vanilla's true physical-stall result always calls stop(), which clears activeBoundedPath.
        // A live newly accepted bounded route must not inherit the previous route's private isStuck flag.
        return path != null && path == activeBoundedPath && !path.isDone() ? false : super.isStuck();
    }

    @Override public void setSpeedModifier(double speed) {
        if (!Double.isFinite(speed) || speed < 0) return;
        if (mob instanceof Human human && club.someoneice.humangunner.HumanGoalExecutionBridge.bound(human)
                && club.someoneice.humangunner.HumanNavigationOwnership.writeToken(human) == null) return;
        super.setSpeedModifier(speed);
    }
    @Override public void tick() {
        if (mob instanceof Human human && movementOwnership != null) {
            if (!movementOwnership.runInstalled(human, path, this::tickOwnedRoute)) {
                discardRoute(); movementOwnership.stopped();
            }
        } else tickOwnedRoute();
    }
    private void tickOwnedRoute() {
        if(mob instanceof Human human)club.someoneice.humangunner.HumanPathFollower.beforeTick(human,path);
        if (mob instanceof Human human) advancePassedCombatWaypoint(human);
        if (mob instanceof Human human && path != null && !path.isDone()) {
            var from = human.blockPosition();
            var to = path.getNextNodePos();
            var reason = HumanTerrainSafety.transitionReason(human, from, to);
            if (reason != HumanTerrainSafety.Reason.SAFE) {
                CombatTerrainNavigation.blocked(human, new HumanTerrainSafety.Assessment(reason, from, to));
                stop();
            }
        }
        super.tick();
        sampledPath = path;
        sampledNode = path == null ? -1 : path.getNextNodeIndex();
        sampledPosition = mob.position();
        sampledTick = mob.tickCount;
        sampledAirborne = !mob.onGround();
    }

    @Override protected void followThePath(){
        super.followThePath();
        if(mob instanceof Human human)club.someoneice.humangunner.HumanPathFollower.follow(human,path);
    }

    /** An airborne body can cross a flat node while missing vanilla's vertical arrival tolerance. */
    private void advancePassedCombatWaypoint(Human human) {
        if (human.level().isClientSide || path == null || path.isDone() || path != sampledPath
                || sampledPosition == null || sampledTick != human.tickCount - 1
                || sampledNode != path.getNextNodeIndex() || human.onGround() && !sampledAirborne
                || human.getTarget() == null || !human.getTarget().isAlive() || human.isFleeing
                || human.isInWater() || human.isInLava() || human.onClimbable() || human.isPassenger()
                || human.isNoAi() || human.isSleeping() || human.hurtTime > 0) return;
        int index = path.getNextNodeIndex();
        if (index + 1 >= path.getNodeCount()) return;
        Node node = path.getNode(index), next = path.getNode(index + 1);
        if (node.type != BlockPathTypes.WALKABLE || next.type != BlockPathTypes.WALKABLE
                || node.y != next.y || human.getY() < node.y - .1 || human.getY() > node.y + 1.75) return;
        var point = path.getEntityPosAtNode(human, index);
        var destination = path.getEntityPosAtNode(human, index + 1);
        var direction = destination.subtract(point).multiply(1, 0, 1);
        if (index > 0) {
            Node previous = path.getNode(index - 1);
            var incoming = point.subtract(path.getEntityPosAtNode(human, index - 1)).multiply(1, 0, 1);
            if (previous.y != node.y || incoming.lengthSqr() < .01
                    || incoming.normalize().dot(direction.normalize()) < .99) return;
        }
        double corridor = Math.min(.6, Math.max(.25, human.getBbWidth() * .75));
        if (!dev.felix.hostilehumans.core.PassedWaypointPolicy.crossed(sampledPosition.x, sampledPosition.z,
                human.getX(), human.getZ(), point.x, point.z, destination.x, destination.z, corridor)) return;
        // Ground-level clearance is deliberately conservative: no cutting a door, ladder or wall corner.
        var offset = destination.subtract(human.position()).multiply(1, 0, 1);
        var swept = human.getBoundingBox().move(0, node.y - human.getY(), 0)
                .expandTowards(offset).deflate(1.0E-4);
        BlockPos low = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos high = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        if (!level.hasChunksAt(low, high) || !level.getWorldBorder().isWithinBounds(low)
                || !level.getWorldBorder().isWithinBounds(high)) return;
        for (BlockPos occupied : BlockPos.betweenClosed(low, high)) {
            var state = level.getBlockState(occupied);
            if (!level.getFluidState(occupied).isEmpty() || state.is(net.minecraft.tags.BlockTags.CLIMBABLE)
                    || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) return;
        }
        if (!level.noCollision(human, swept) || !HumanTerrainSafety.safeStep(human, offset)
                || !HumanTerrainSafety.safeTransition(human, human.blockPosition(), path.getNodePos(index + 1))
                || !club.someoneice.humangunner.SoldierOrder.allowsCombatPosition(human, destination)) return;
        path.advance(); // At most one adjacent node; keep the route and its owner unchanged.
    }

    @Override protected PathFinder createPathFinder(int budget) {
        nodeEvaluator = new HumanNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, budget);
    }

    private final class HumanNodeEvaluator extends WalkNodeEvaluator {
        @Override public BlockPathTypes getBlockPathType(BlockGetter getter, int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = getter.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.getBlockPathType(getter, pos, mob) == null)
                return state.getValue(FenceGateBlock.OPEN) ? BlockPathTypes.WALKABLE : BlockPathTypes.DOOR_WOOD_CLOSED;
            if (state.getBlock() instanceof DoorBlock && PassageLease.handOpenable(state))
                return state.getValue(DoorBlock.OPEN) ? BlockPathTypes.DOOR_OPEN : BlockPathTypes.DOOR_WOOD_CLOSED;
            return super.getBlockPathType(getter, x, y, z);
        }
        @Override public int getNeighbors(Node[] neighbors, Node node) {
            expandedNodes++;
            int count = super.getNeighbors(neighbors, node);
            if (count < neighbors.length)
                count = HumanUtil.createLadderNodeFor(count, neighbors, node, this::getNode, level, mob);
            if (!(mob instanceof Human human)) return count;
            int accepted = 0;
            BlockPos from = new BlockPos(node.x, node.y, node.z);
            for (int index = 0; index < count; index++) {
                Node next = neighbors[index];
                if (next != null && !isAvoided(from, new BlockPos(next.x, next.y, next.z))
                        && HumanTerrainSafety.safeTransition(human, from, new BlockPos(next.x, next.y, next.z)))
                    neighbors[accepted++] = next;
            }
            return accepted;
        }
    }
}
