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

/** Human-only navigation. Other mobs' fence/door classification is untouched. */
public final class HumanNavigation extends GroundPathNavigation {
    private BlockPos temporarilyBlockedNode;
    private long blockedUntilTick;

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

    /** Exclude a waypoint that repeatedly traps this human from the next path search. */
    public void avoidWaypoint(BlockPos node, long untilTick) {
        temporarilyBlockedNode = node.immutable();
        blockedUntilTick = untilTick;
    }

    private boolean isAvoided(BlockPos pos) {
        return temporarilyBlockedNode != null && level.getGameTime() < blockedUntilTick
                && temporarilyBlockedNode.equals(pos);
    }

    @Override protected PathFinder createPathFinder(int budget) {
        nodeEvaluator = new HumanNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, budget);
    }
    private final class HumanNodeEvaluator extends WalkNodeEvaluator {
        @Override public BlockPathTypes getBlockPathType(BlockGetter getter, int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isAvoided(pos)) return BlockPathTypes.BLOCKED;
            var state = getter.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.getBlockPathType(getter, pos, mob) == null)
                return state.getValue(FenceGateBlock.OPEN) ? BlockPathTypes.WALKABLE : BlockPathTypes.DOOR_WOOD_CLOSED;
            if (state.getBlock() instanceof DoorBlock && PassageLease.handOpenable(state))
                return state.getValue(DoorBlock.OPEN) ? BlockPathTypes.DOOR_OPEN : BlockPathTypes.DOOR_WOOD_CLOSED;
            return super.getBlockPathType(getter, x, y, z);
        }
        @Override public int getNeighbors(Node[] neighbors, Node node) {
            int count = super.getNeighbors(neighbors, node);
            if (count >= neighbors.length) return count;
            return HumanUtil.createLadderNodeFor(count, neighbors, node, this::getNode, level, mob);
        }
    }
}
