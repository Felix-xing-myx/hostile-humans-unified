package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import dev.felix.hostilehumans.core.IncrementalBestSearch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Finds a reachable nearby position with a clear shot, without taking over retreat behavior. */
public final class RangedFiringPosition {
    // Values contain only geometry, identifiers and paths, never their Human
    // key or the world. Interrupted searches cannot keep unloaded entities alive.
    private static final Map<Human, VisibleSearch> VISIBLE_SEARCHES = new WeakHashMap<>();
    private static final class SearchVisit {
        int tick;
        SearchVisit(int tick) { this.tick = tick; }
    }
    private record VisibleSearch(UUID target, ResourceKey<Level> dimension, Item weapon, Vec3 origin, Vec3 targetPosition,
                                 double minimum, double maximum, int radius, int attempts,
                                 SearchVisit visit, IncrementalBestSearch<BlockPos, Path> search) {}

    public static boolean hasPendingVisibleSearch(Human human) { return VISIBLE_SEARCHES.containsKey(human); }
    public static void cancelVisibleSearch(Human human) {
        VISIBLE_SEARCHES.remove(human);
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.FIRING_LANE);
    }
    public static int retryDelay(Human human, Path result, int failureDelay) {
        return retryDelay(hasPendingVisibleSearch(human), result != null, failureDelay);
    }
    static int retryDelay(boolean pending, boolean found, int failureDelay) {
        return pending ? 1 : found ? 10 : failureDelay;
    }
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
                || human.level().isClientSide || target == null || !target.isAlive()
                || target.level() != human.level()) {
            cancelVisibleSearch(human);
            return null;
        }

        double currentRange = human.distanceTo(target);
        double preferredRange = Math.max(minimumRange, Math.min(maximumRange, currentRange));
        double minimumRangeSqr = minimumRange * minimumRange;
        double maximumRangeSqr = maximumRange * maximumRange;
        VisibleSearch session = VISIBLE_SEARCHES.get(human);
        if (session == null || !session.target().equals(target.getUUID())
                || !session.dimension().equals(human.level().dimension())
                || session.weapon() != human.getMainHandItem().getItem()
                || session.origin().distanceToSqr(human.position()) > 4.0D
                || session.targetPosition().distanceToSqr(target.position()) > 4.0D
                || Math.abs(session.minimum() - minimumRange) > 0.5D
                || Math.abs(session.maximum() - maximumRange) > 0.5D
                || session.radius() != searchRadius || session.attempts() != attempts
                || human.tickCount < session.visit().tick || human.tickCount - session.visit().tick > 20) {
            List<BlockPos> candidates = new ArrayList<>();
            HashSet<BlockPos> unique = new HashSet<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                Vec3 candidate = DefaultRandomPos.getPos(human, searchRadius, 4);
                if (candidate == null) continue;
                double sampledRange = Math.sqrt(target.distanceToSqr(candidate));
                if (sampledRange < Math.max(0.0D, minimumRange - 2.0D)
                        || sampledRange > maximumRange + 2.0D
                        || candidate.distanceToSqr(human.position()) < 1.0D) continue;
                BlockPos candidateBlock = BlockPos.containing(candidate);
                if (unique.add(candidateBlock)) candidates.add(candidateBlock);
            }
            session = new VisibleSearch(target.getUUID(), human.level().dimension(), human.getMainHandItem().getItem(),
                    human.position(), target.position(),
                    minimumRange, maximumRange, searchRadius, attempts, new SearchVisit(human.tickCount),
                    new IncrementalBestSearch<>(candidates.iterator()));
            VISIBLE_SEARCHES.put(human, session);
        }
        // Budget waiting is live work, not an abandoned search. Refresh only
        // its visit time; movement/target/weapon validation still runs above.
        session.visit().tick = human.tickCount;
        int budget = OptionalPathBudget.claim(human, OptionalPathBudget.Kind.FIRING_LANE, 4);
        if (budget == 0) return null;
        Map<BlockPos, Boolean> clearShots = new HashMap<>();
        Path bestPath = session.search().advance(budget, candidateBlock -> {
            Vec3 candidateFeet = Vec3.atBottomCenterOf(candidateBlock);
            double candidateRangeSqr = target.distanceToSqr(candidateFeet);
            if (candidateRangeSqr < minimumRangeSqr || candidateRangeSqr > maximumRangeSqr
                    || candidateFeet.distanceToSqr(human.position()) < 4.0D
                    || !human.level().hasChunkAt(candidateBlock)
                    || !clearShots.computeIfAbsent(candidateBlock, pos -> hasClearShot(human,
                    Vec3.atBottomCenterOf(pos).add(0.0D, human.getEyeHeight(), 0.0D), target.getEyePosition()))) {
                return null;
            }
            Path path = human.getNavigation().createPath(candidateBlock, 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) {
                return null;
            }
            return path;
        }, path -> {
            Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());
            double targetDistanceSqr = target.distanceToSqr(destination);
            if (targetDistanceSqr < minimumRangeSqr || targetDistanceSqr > maximumRangeSqr
                    || destination.distanceToSqr(human.position()) < 4.0D) {
                return Double.NEGATIVE_INFINITY;
            }

            Vec3 destinationEye = destination.add(0.0D, human.getEyeHeight(), 0.0D);
            if (!clearShots.computeIfAbsent(path.getTarget(), pos ->
                    hasClearShot(human, destinationEye, target.getEyePosition()))) {
                return Double.NEGATIVE_INFINITY;
            }

            double targetDistance = Math.sqrt(targetDistanceSqr);
            double travelDistance = Math.sqrt(destination.distanceToSqr(human.position()));
            double verticalChange = Math.abs(destination.y - human.getY());
            double score = Math.abs(targetDistance - preferredRange) * 3.0D
                    + travelDistance * 0.35D
                    + path.getNodeCount() * 0.55D
                    + verticalChange * 2.0D;
            if (human.isInWater()
                    && !human.level().getFluidState(path.getTarget()).is(FluidTags.WATER)) {
                // A clear dry firing lane is preferable, but never mandatory:
                // an archer can still shoot while crossing a river.
                score -= 12.0D;
            }
            return -score;
        });
        if (session.search().hasRemaining()) return null;
        cancelVisibleSearch(human);
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
        return findLateralPath(human, target, minimumRange, maximumRange,
                sideDirection, human.getX(), human.getZ(), Double.MAX_VALUE);
    }

    public static Path findLateralPath(
            Human human,
            LivingEntity target,
            double minimumRange,
            double maximumRange,
            int sideDirection,
            double anchorX,
            double anchorZ,
            double anchorRadius
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
        double preferredRange = (lowerRange + upperRange) * 0.5D;
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
            double anchorDx = candidate.x - anchorX;
            double anchorDz = candidate.z - anchorZ;
            if (anchorDx * anchorDx + anchorDz * anchorDz > anchorRadius * anchorRadius) {
                continue;
            }
            BlockPos candidateBlock = BlockPos.containing(candidate.x, human.getY(), candidate.z);
            if (candidateBlock.distSqr(human.blockPosition()) < 16.0D) {
                continue;
            }
            Vec3 candidateFeet = Vec3.atBottomCenterOf(candidateBlock);
            double candidateRangeSqr = target.distanceToSqr(candidateFeet);
            if (candidateRangeSqr < minimumRangeSqr || candidateRangeSqr > maximumRangeSqr
                    || candidateFeet.distanceToSqr(human.position()) < 30.25D
                    || !human.level().hasChunkAt(candidateBlock)
                    || !hasClearShot(human, candidateFeet
                    .add(0.0D, human.getEyeHeight(), 0.0D), target.getEyePosition())) {
                continue;
            }
            Path path = human.getNavigation().createPath(candidateBlock, 0);
            if (path == null || !path.canReach() || path.getNodeCount() < 2) {
                continue;
            }

            Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());
            double destinationDx = destination.x - anchorX;
            double destinationDz = destination.z - anchorZ;
            if (destinationDx * destinationDx + destinationDz * destinationDz
                    > anchorRadius * anchorRadius) {
                continue;
            }
            double targetDistanceSqr = target.distanceToSqr(destination);
            double travelDistanceSqr = human.position().distanceToSqr(destination);
            if (targetDistanceSqr < minimumRangeSqr || targetDistanceSqr > maximumRangeSqr
                    || travelDistanceSqr < 30.25D) {
                continue;
            }
            Vec3 destinationEye = destination.add(0.0D, human.getEyeHeight(), 0.0D);
            if (!path.getTarget().equals(candidateBlock)
                    && !hasClearShot(human, destinationEye, target.getEyePosition())) {
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
