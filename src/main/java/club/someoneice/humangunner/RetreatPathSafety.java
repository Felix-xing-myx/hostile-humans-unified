package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Shared safety gate for every retreat entry, including local planning fallback. */
public final class RetreatPathSafety {
    private RetreatPathSafety() {}

    /** Navigation may install a candidate before reporting failure; restore its live predecessor. */
    public static boolean tryMove(Human human, Path path, double speed) {
        if (path == null || !path.canReach() || path.isDone()) return false;
        Path previous = human.getNavigation().getPath();
        if (human.getNavigation().moveTo(path, speed)) return true;
        if (previous != null && !previous.isDone() && human.getNavigation().getPath() != previous)
            human.getNavigation().moveTo(previous, speed);
        return false;
    }

    public static boolean isSafe(Human human, LivingEntity threat, Path path, boolean requireGain) {
        return isSafe(human, (net.minecraft.world.entity.Entity)threat, path, requireGain);
    }
    public static boolean isSafe(Human human, net.minecraft.world.entity.Entity threat, Path path, boolean requireGain) {
        if (path == null || !path.canReach() || path.isDone() || threat == null) return false;
        if (!HumanTerrainSafety.safePath(human, path)) return false;
        int first = path.getNextNodeIndex();
        // A freshly planned route includes the occupied source block. Its
        // center rounding is not an actual approach step; vanilla advances it.
        if (first + 1 < path.getNodeCount()
                && path.getNodePos(first).getX() == human.blockPosition().getX()
                && path.getNodePos(first).getZ() == human.blockPosition().getZ()
                && Math.abs(path.getNodePos(first).getY() - human.getY()) < 1.0D) first++;
        int count = path.getNodeCount() - first;
        if (count <= 0 || count > RetreatPathSafetyPolicy.MAX_NODES) return false;
        double[][] nodes = new double[count][2];
        for (int i = 0; i < count; i++) {
            BlockPos pos = path.getNodePos(first + i);
            if (!human.level().hasChunkAt(pos) || !human.level().getWorldBorder().isWithinBounds(pos) || isHazard(human, pos)
                    || isHazard(human, pos.above()) || isHazard(human, pos.below())) return false;
            nodes[i][0] = pos.getX() + 0.5D;
            nodes[i][1] = pos.getZ() + 0.5D;
        }
        return RetreatPathSafetyPolicy.isSafe(human.getX(), human.getZ(), threat.getX(), threat.getZ(),
                nodes, requireGain);
    }

    public static boolean isHazard(Human human, BlockPos pos) {
        var state = human.level().getBlockState(pos);
        return human.level().getFluidState(pos).is(FluidTags.LAVA)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
    }

    public static boolean tryLocalStep(Human human, LivingEntity threat, double speed) {
        if (threat == null || !human.getNavigation().isDone()) return false;
        Vec3 away = human.position().subtract(threat.position()).multiply(1, 0, 1).normalize().scale(0.65D);
        if (tryStep(human, threat, away, speed)) return true;
        Vec3 side = new Vec3(-away.z, 0, away.x);
        return tryStep(human, threat, side, speed) || tryStep(human, threat, side.scale(-1), speed);
    }

    private static boolean tryStep(Human human, LivingEntity threat, Vec3 offset, double speed) {
        if (offset.lengthSqr() < 0.01D) return false;
        Vec3 candidate = human.position().add(offset);
        BlockPos feet = BlockPos.containing(candidate);
        var swept = human.getBoundingBox().expandTowards(offset);
        if (!human.level().hasChunkAt(feet) || isHazard(human, feet) || isHazard(human, feet.above())
                || isHazard(human, feet.below())
                || !human.level().noCollision(human, swept)
                || !human.isInWater() && (!human.level().getFluidState(feet).isEmpty()
                || !human.level().getFluidState(feet.below()).isEmpty()
                || !human.level().getBlockState(feet.below()).isFaceSturdy(human.level(), feet.below(), Direction.UP))
                || candidate.distanceToSqr(threat.position()) + 1.0E-6D < human.distanceToSqr(threat)) return false;
        // Check hazards under the whole swept body, including diagonal corner
        // tiles; destination-only tests can step across a lava edge.
        int probes = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY - 0.1D, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            if (++probes > 64 || !human.level().hasChunkAt(pos) || isHazard(human, pos)) return false;
        }
        human.getMoveControl().setWantedPosition(candidate.x, human.getY(), candidate.z, speed);
        return true;
    }
}
