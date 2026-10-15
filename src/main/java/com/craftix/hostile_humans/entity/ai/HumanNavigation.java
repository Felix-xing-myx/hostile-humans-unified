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
public final class HumanNavigation extends GroundPathNavigation {
    private record BlockedEdge(BlockPos from, BlockPos to, long until) {}
    private final Deque<BlockedEdge> blockedEdges = new ArrayDeque<>();
    private Path sampledPath;
    private net.minecraft.world.phys.Vec3 sampledPosition;
    private int sampledNode, sampledTick;
    private boolean sampledAirborne;

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
        if (mob instanceof Human human && path != null && !path.isDone()) {
            var assessment = HumanTerrainSafety.assessPath(human, path);
            if (!assessment.safe()) { CombatTerrainNavigation.blocked(human, assessment); return false; }
        }
        boolean accepted = super.moveTo(path, speed);
        if (mob instanceof Human human && path != null)
            club.someoneice.humangunner.NavigationDebugService.candidate(human, net.minecraft.world.phys.Vec3.atBottomCenterOf(path.getTarget()),
                    "installed", accepted ? "accepted" : "rejected", Double.NaN);
        return accepted;
    }

    @Override public void tick() {
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
