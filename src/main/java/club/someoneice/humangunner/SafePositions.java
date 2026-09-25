package club.someoneice.humangunner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Bounded, loaded-chunk-only humanoid placement shared by orders and flares. */
final class SafePositions {
    private static final int[] FLOOR_OFFSETS = {0, 1, -1, 2, -2, 3, -3, -4};
    static BlockPos nearFloor(ServerLevel level, int x, int z, int y) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        for (int dy : FLOOR_OFFSETS) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (isSafe(level, pos)) return pos;
        }
        return null;
    }
    static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 2 >= level.getMaxBuildHeight()
                || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                || !level.getFluidState(pos.below()).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        return level.noCollision(new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8));
    }
}
