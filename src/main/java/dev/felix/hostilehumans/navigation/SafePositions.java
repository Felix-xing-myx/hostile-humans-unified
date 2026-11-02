package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.behavior.combat.retreat.RetreatPathSafety;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Bounded, loaded-chunk-only humanoid placement shared by orders and flares. */
public final class SafePositions {
    /** A dry floor or actual water surface near the owner; never loads chunks. */
    public static net.minecraft.world.phys.Vec3 nearOwner(dev.felix.hostilehumans.entity.Human human,
            net.minecraft.server.level.ServerPlayer owner, int x, int z) {
        ServerLevel level = owner.serverLevel();
        BlockPos floor = nearFloor(level, x, z, owner.getBlockY());
        net.minecraft.world.phys.Vec3 candidate = floor == null ? null
                : new net.minecraft.world.phys.Vec3(x + 0.5, floor.getY(), z + 0.5);
        if (candidate == null && level.hasChunk(x >> 4, z >> 4)) {
            for (int dy : FLOOR_OFFSETS) {
                BlockPos pos = new BlockPos(x, owner.getBlockY() + dy, z);
                var fluid = level.getFluidState(pos);
                if (!fluid.is(net.minecraft.tags.FluidTags.WATER)
                        || !level.getFluidState(pos.above()).isEmpty()) continue;
                double surface = pos.getY() + fluid.getHeight(level, pos);
                candidate = new net.minecraft.world.phys.Vec3(x + 0.5,
                        surface - ShoreSeekingPolicy.SURFACE_STANCE_DEPTH, z + 0.5);
                break;
            }
        }
        return checkedPlacement(human, candidate);
    }

    static net.minecraft.world.phys.Vec3 nearPost(dev.felix.hostilehumans.entity.Human human, BlockPos post, int x, int z) {
        if (!(human.level() instanceof ServerLevel level)) return null;
        BlockPos floor = nearFloor(level, x, z, post.getY());
        return checkedPlacement(human, floor == null ? null
                : new net.minecraft.world.phys.Vec3(x + 0.5, floor.getY(), z + 0.5));
    }

    private static net.minecraft.world.phys.Vec3 checkedPlacement(
            dev.felix.hostilehumans.entity.Human human, net.minecraft.world.phys.Vec3 candidate) {
        if (!(human.level() instanceof ServerLevel level)) return null;
        return checkedPlacement(human, level, candidate);
    }
    /** Explicit destination world for cross-dimension rescue; still never requests a chunk. */
    public static net.minecraft.world.phys.Vec3 checkedPlacement(
            dev.felix.hostilehumans.entity.Human human, ServerLevel level, net.minecraft.world.phys.Vec3 candidate) {
        if (candidate == null || !level.getWorldBorder().isWithinBounds(BlockPos.containing(candidate))) return null;
        AABB box = human.getBoundingBox().move(candidate.subtract(human.position()));
        if (box.minY <= level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()) return null;
        for (int cx = ((int)Math.floor(box.minX)) >> 4; cx <= ((int)Math.floor(box.maxX)) >> 4; cx++)
            for (int cz = ((int)Math.floor(box.minZ)) >> 4; cz <= ((int)Math.floor(box.maxZ)) >> 4; cz++)
                if (!level.hasChunk(cx, cz)) return null;
        var border = level.getWorldBorder();
        if (box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return null;
        for (BlockPos occupied : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.01, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (RetreatPathSafety.isHazard(level, occupied)) return null;
        return level.noCollision(human, box) ? candidate : null;
    }
    private static final int[] FLOOR_OFFSETS = {0, 1, -1, 2, -2, 3, -3, -4};
    public static BlockPos nearFloor(ServerLevel level, int x, int z, int y) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        for (int dy : FLOOR_OFFSETS) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (isSafe(level, pos)) return pos;
        }
        return null;
    }
    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 2 >= level.getMaxBuildHeight()
                || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                || !level.getFluidState(pos.below()).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        for (BlockPos check : new BlockPos[] {pos, pos.above(), pos.below()}) {
            var state = level.getBlockState(check);
            if (state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    || state.is(net.minecraft.tags.BlockTags.FIRE)
                    || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    || ((state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                    || state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE))
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))) return false;
        }
        return level.noCollision(new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8));
    }
}
