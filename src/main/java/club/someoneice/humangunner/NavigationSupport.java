package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Small, deterministic helpers shared by normal pursuit and retreat movement. */
final class NavigationSupport {
    private NavigationSupport() {
    }

    static boolean openBlockingPassage(Human human) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos feet = human.blockPosition();
        candidates.add(feet);
        candidates.add(feet.above());

        Path path = human.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            BlockPos next = path.getNextNodePos();
            candidates.add(next);
            candidates.add(next.above());
            candidates.add(next.below());
        }

        Vec3 motion = human.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        if (motion.lengthSqr() > 0.0025D) {
            Vec3 ahead = human.position().add(motion.normalize().scale(0.9D));
            BlockPos forward = BlockPos.containing(ahead);
            candidates.add(forward);
            candidates.add(forward.above());
        }

        boolean opened = false;
        for (BlockPos pos : candidates) {
            BlockState state = human.level().getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.OPEN)
                    || state.getValue(BlockStateProperties.OPEN)) {
                continue;
            }
            if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
                door.setOpen(human, human.level(), state, pos, true);
                opened = true;
            } else if (state.getBlock() instanceof TrapDoorBlock
                    && !state.is(Blocks.IRON_TRAPDOOR)) {
                human.level().setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                opened = true;
            }
        }
        return opened;
    }

    static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 0.01D ? Vec3.ZERO : direction.normalize();
    }

    static boolean pathStartsGenerallyToward(Path path, Vec3 origin, Vec3 desiredDirection) {
        if (path == null || path.isDone() || desiredDirection.lengthSqr() < 0.01D) {
            return false;
        }
        int first = path.getNextNodeIndex();
        int limit = Math.min(path.getNodeCount(), first + 4);
        for (int i = first; i < limit; i++) {
            Vec3 pathDirection = horizontalDirection(origin, Vec3.atCenterOf(path.getNodePos(i)));
            if (pathDirection.lengthSqr() > 0.01D) {
                return pathDirection.dot(desiredDirection) >= -0.10D;
            }
        }
        return false;
    }
}
