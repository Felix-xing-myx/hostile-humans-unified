package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Movement owned by the existing defensive goal, never a second navigation goal. */
final class ShieldDefenseMovement {
    private boolean disengaging;
    private Path route, best;
    private double bestScore;
    private int remaining, nextSearch, nextValidation;
    private LivingEntity opponent;
    private Vec3 progressPosition;
    private int progressTick;
    private int nextCoverCheck;
    private boolean currentCovered;

    boolean tick(Human human, LivingEntity threat, CombatAiConfig config, boolean forced) {
        double health = human.getHealth() / Math.max(1.0F, human.getMaxHealth());
        if (!forced || threat == null || health >= config.resumeHealthRatio()) {
            clear(human);
            return false;
        }
        if (!disengaging && health > config.retreatHealthRatio()) return false;
        if (!disengaging || opponent != threat) {
            clear(human);
            disengaging = true;
            opponent = threat;
            // Discard pursuit before water/terrain combat can advance toward the enemy.
            CombatTerrainNavigation.clear(human);
            WaterCombatNavigation.clear(human);
            MovementContinuity.cancelStep(human);
            human.getNavigation().stop();
            human.clearRangedStrafeMotion();
            progressPosition = human.position();
            progressTick = human.tickCount;
        }
        MovementSpeedController.combat(human, false);
        if (SoldierOrder.isHoldingPosition(human)) {
            human.getNavigation().stop();
            best = null;
            remaining = 0;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.RETREAT);
            return true;
        }
        if (human.tickCount >= nextCoverCheck) {
            currentCovered = covered(human, threat, human.position());
            nextCoverCheck = human.tickCount + 8;
        }
        if (currentCovered && human.distanceToSqr(threat) > 36) {
            stopRoute(human);
            best = null;
            remaining = 0;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.RETREAT);
            return true;
        }
        if (human.position().distanceToSqr(progressPosition) >= .25) {
            progressPosition = human.position();
            progressTick = human.tickCount;
        }
        if (route != null && human.tickCount >= nextValidation) {
            nextValidation = human.tickCount + 8;
            if (!MovementContinuity.ownsRoute(human, route) || route.isDone()
                    || human.getNavigation().isStuck() || human.tickCount - progressTick > 40
                    || !safe(human, threat, route)) {
                stopRoute(human);
                nextSearch = human.tickCount;
            }
        }
        // Keep a useful route intact instead of replacing it every search interval.
        if (route != null && !route.isDone() && remaining == 0) return true;
        if (remaining == 0) {
            if (human.tickCount < nextSearch) return true;
            remaining = Math.min(12, config.coverSearchAttempts());
            best = null;
            bestScore = Double.NEGATIVE_INFINITY;
        }
        int budget = OptionalPathBudget.claim(human, OptionalPathBudget.Kind.RETREAT, 2);
        int radius = Math.min(14, config.coverSearchRadius());
        for (int i = 0; i < budget && remaining > 0; i++, remaining--) {
            // Include lateral candidates: cover need not lie directly behind us.
            Vec3 candidate = remaining % 2 == 0
                    ? DefaultRandomPos.getPosAway(human, radius, 3, threat.position())
                    : DefaultRandomPos.getPos(human, radius, 3);
            if (candidate == null || threat.distanceToSqr(candidate) < human.distanceToSqr(threat)
                    || !SoldierOrder.allowsCombatPosition(human, candidate)) continue;
            Path path = human.getNavigation().createPath(BlockPos.containing(candidate), 0);
            if (!safe(human, threat, path)) continue;
            Vec3 end = Vec3.atBottomCenterOf(path.getTarget());
            double score = (covered(human, threat, end) ? 1000 : 0)
                    + Math.min(25, end.distanceTo(threat.position())) * 2
                    - path.getNodeCount() - Math.abs(end.y - human.getY()) * 2;
            if (score > bestScore) { best = path; bestScore = score; }
        }
        if (remaining == 0) {
            // Revalidate after incremental planning; targets and soldiers can move.
            if (best != null && safe(human, threat, best) && RetreatPathSafety.tryMove(human, best, 1.0D)) {
                route = human.getNavigation().getPath();
                progressPosition = human.position();
                progressTick = human.tickCount;
                nextValidation = human.tickCount + 8;
            }
            best = null;
            nextSearch = human.tickCount + 20;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.RETREAT);
        }
        // No valid route: keep shielding, never fall through to pursuit.
        return true;
    }

    private static boolean covered(Human human, LivingEntity threat, Vec3 feet) {
        // Both upper and lower body must be screened; eye-only cover can expose the torso.
        for (double height : new double[]{human.getEyeHeight(), human.getBbHeight() * .5}) {
            if (human.level().clip(new ClipContext(threat.getEyePosition(), feet.add(0, height, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType() == HitResult.Type.MISS)
                return false;
        }
        return true;
    }

    private static boolean safe(Human human, LivingEntity threat, Path path) {
        return RetreatPathSafety.isSafe(human, threat, path, false)
                && SoldierOrder.allowsCombatPath(human, path);
    }

    private void stopRoute(Human human) {
        if (route != null && MovementContinuity.ownsRoute(human, route)) human.getNavigation().stop();
        route = null;
    }

    void clear(Human human) {
        if (!disengaging) return;
        stopRoute(human);
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.RETREAT);
        disengaging = false;
        opponent = null;
        best = null;
        remaining = nextSearch = 0;
        nextCoverCheck = 0;
        currentCovered = false;
    }
}
