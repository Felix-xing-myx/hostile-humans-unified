package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds a reachable nearby position with a clear shot, without taking over retreat behavior. */
public final class RangedFiringPosition {
    private RangedFiringPosition() {
    }

    public static Path findVisiblePath(
            Human human,
            LivingEntity target,
            double minimumRange,
            double maximumRange,
            int searchRadius,
            int attempts
    ) {
        if (human.isFleeing || SoldierOrder.isHoldingPosition(human)
                || human.level().isClientSide || target == null || !target.isAlive()) {
            return null;
        }

        double currentRange = human.distanceTo(target);
        double preferredRange = Math.max(minimumRange, Math.min(maximumRange, currentRange));
        double minimumRangeSqr = minimumRange * minimumRange;
        double maximumRangeSqr = maximumRange * maximumRange;
        Path bestPath = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int attempt = 0; attempt < attempts; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPos(human, searchRadius, 4);
            if (candidate == null) {
                continue;
            }
            Path path = human.getNavigation().createPath(BlockPos.containing(candidate), 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) {
                continue;
            }

            Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());
            double targetDistanceSqr = target.distanceToSqr(destination);
            if (targetDistanceSqr < minimumRangeSqr || targetDistanceSqr > maximumRangeSqr
                    || destination.distanceToSqr(human.position()) < 4.0D) {
                continue;
            }

            Vec3 destinationEye = destination.add(0.0D, human.getEyeHeight(), 0.0D);
            if (!hasClearShot(human, destinationEye, target.getEyePosition())) {
                continue;
            }

            double targetDistance = Math.sqrt(targetDistanceSqr);
            double travelDistance = Math.sqrt(destination.distanceToSqr(human.position()));
            double verticalChange = Math.abs(destination.y - human.getY());
            double score = Math.abs(targetDistance - preferredRange) * 3.0D
                    + travelDistance * 0.35D
                    + path.getNodeCount() * 0.55D
                    + verticalChange * 2.0D;
            if (score < bestScore) {
                bestScore = score;
                bestPath = path;
            }
        }
        return bestPath;
    }

    /**
     * Pick a reachable, visibly clear lateral firing position. The lateral
     * offset is intentionally several blocks so an armed Human makes a real
     * tactical reposition instead of the barely perceptible strafe input used
     * by MoveControl.
     */
    public static Path findLateralPath(
            Human human,
            LivingEntity target,
            double minimumRange,
            double maximumRange,
            int sideDirection
    ) {
        if (human.isFleeing || SoldierOrder.isHoldingPosition(human)
                || human.level().isClientSide || target == null || !target.isAlive()) {
            return null;
        }

        Vec3 outward = NavigationSupport.horizontalDirection(target.position(), human.position());
        if (outward.lengthSqr() < 0.01D) {
            return null;
        }
        Vec3 lateral = new Vec3(-outward.z, 0.0D, outward.x)
                .scale(sideDirection < 0 ? -1.0D : 1.0D);
        double margin = Math.min(2.0D, Math.max(0.0D, (maximumRange - minimumRange) * 0.2D));
        double lowerRange = minimumRange + margin;
        double upperRange = Math.max(lowerRange, maximumRange - margin);
        double preferredRange = Math.max(lowerRange,
                Math.min(upperRange, human.distanceTo(target)));
        double minimumRangeSqr = minimumRange * minimumRange;
        double maximumRangeSqr = maximumRange * maximumRange;
        Path bestPath = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (double lateralDistance : new double[]{6.0D, 8.0D, 10.0D}) {
            double radialSqr = preferredRange * preferredRange
                    - lateralDistance * lateralDistance;
            if (radialSqr <= 0.0D) {
                continue;
            }
            double radialDistance = Math.sqrt(radialSqr);
            Vec3 candidate = target.position()
                    .add(outward.scale(radialDistance))
                    .add(lateral.scale(lateralDistance));
            BlockPos candidateBlock = BlockPos.containing(candidate.x, human.getY(), candidate.z);
            Path path = human.getNavigation().createPath(candidateBlock, 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) {
                continue;
            }

            Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());
            double targetDistanceSqr = target.distanceToSqr(destination);
            double travelDistanceSqr = human.position().distanceToSqr(destination);
            if (targetDistanceSqr < minimumRangeSqr || targetDistanceSqr > maximumRangeSqr
                    || travelDistanceSqr < 30.25D) {
                continue;
            }
            Vec3 destinationEye = destination.add(0.0D, human.getEyeHeight(), 0.0D);
            if (!hasClearShot(human, destinationEye, target.getEyePosition())) {
                continue;
            }

            double targetDistance = Math.sqrt(targetDistanceSqr);
            double travelDistance = Math.sqrt(travelDistanceSqr);
            double score = Math.abs(targetDistance - preferredRange) * 3.0D
                    + Math.abs(travelDistance - lateralDistance) * 0.5D
                    + path.getNodeCount() * 0.25D;
            if (score < bestScore) {
                bestScore = score;
                bestPath = path;
            }
        }
        return bestPath;
    }

    private static boolean hasClearShot(Human human, Vec3 from, Vec3 to) {
        return human.level().clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                human
        )).getType() == HitResult.Type.MISS;
    }
}
