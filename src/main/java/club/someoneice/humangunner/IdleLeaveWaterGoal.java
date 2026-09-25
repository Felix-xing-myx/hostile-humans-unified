package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/** Simple idle-only request to use native navigation toward nearby dry land. */
public final class IdleLeaveWaterGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int SEARCH_ATTEMPTS = 24;
    private static final double NAVIGATION_SPEED = 1.0D;

    private final Human human;
    private Path shorePath;
    private int nextSearchTick;

    public IdleLeaveWaterGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!isIdleInWater() || human.tickCount < nextSearchTick) {
            return false;
        }
        shorePath = findShorePath();
        if (shorePath == null) {
            nextSearchTick = human.tickCount + 40;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return isIdleInWater() && !human.getNavigation().isDone();
    }

    @Override
    public void start() {
        MovementSpeedController.normal(human);
        human.setSprinting(false);
        human.getNavigation().moveTo(shorePath, NAVIGATION_SPEED);
        human.getPersistentData().putString("humangunner:ai_phase", "idle_leaving_water");
    }

    @Override
    public void stop() {
        shorePath = null;
        nextSearchTick = human.tickCount + (human.isInWater() ? 30 : 100);
        if (human.getTarget() == null) {
            MovementSpeedController.normal(human);
            human.getPersistentData().putString("humangunner:ai_phase", "idle");
        }
    }

    private boolean isIdleInWater() {
        return human.isAlive()
                && !human.isPassenger()
                && human.getTarget() == null
                && !SoldierOrder.isHoldingPosition(human)
                && human.isInWater()
                && (human.getLastHurtByMob() == null
                || human.tickCount - human.getLastHurtByMobTimestamp() > 100);
    }

    private Path findShorePath() {
        BlockPos origin = human.blockPosition();
        Path bestPath = null;
        double bestDistance = Double.MAX_VALUE;
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            double angle = human.getRandom().nextDouble() * Math.PI * 2.0D;
            int distance = human.getRandom().nextInt(3, SEARCH_RADIUS + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, origin.getY(), z);
            if (!human.level().hasChunkAt(probe)) {
                continue;
            }
            int y = human.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (!isDryStandable(candidate)) {
                continue;
            }
            Path path = human.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) {
                continue;
            }
            double distanceSqr = candidate.distSqr(origin);
            if (distanceSqr < bestDistance) {
                bestPath = path;
                bestDistance = distanceSqr;
            }
        }
        return bestPath;
    }

    private boolean isDryStandable(BlockPos pos) {
        BlockPos floor = pos.below();
        return human.level().getFluidState(pos).isEmpty()
                && human.level().getFluidState(pos.above()).isEmpty()
                && human.level().getFluidState(floor).isEmpty()
                && human.level().getBlockState(pos).getCollisionShape(human.level(), pos).isEmpty()
                && human.level().getBlockState(pos.above()).getCollisionShape(human.level(), pos.above()).isEmpty()
                && human.level().getBlockState(floor).isFaceSturdy(human.level(), floor, Direction.UP);
    }
}
