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

    public HumanNavigation(Mob mob, Level level) { super(mob, level); }

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
        return super.moveTo(path, speed);
    }

    @Override public void tick() {
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
