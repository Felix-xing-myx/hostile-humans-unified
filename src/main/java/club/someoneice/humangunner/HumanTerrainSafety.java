package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Shared landing and swept-foot safety for paths, strafing and direct movement. */
public final class HumanTerrainSafety {
    private HumanTerrainSafety() {}

    public enum Reason { SAFE, UNAVAILABLE, HAZARD, EXCESSIVE_DROP, UNSUPPORTED }
    public record Assessment(Reason reason, BlockPos from, BlockPos to) {
        private static final Assessment CLEAR = new Assessment(Reason.SAFE, null, null);
        public boolean safe() { return reason == Reason.SAFE; }
    }

    public static boolean safeTransition(Human human, BlockPos from, BlockPos to) {
        return transitionReason(human, from, to) == Reason.SAFE;
    }

    public static Reason transitionReason(Human human, BlockPos from, BlockPos to) {
        if (!human.level().hasChunkAt(to) || !human.level().getWorldBorder().isWithinBounds(to)) return Reason.UNAVAILABLE;
        if (!human.isInLava() && (RetreatPathSafety.isHazard(human, to)
                || RetreatPathSafety.isHazard(human, to.above()) || RetreatPathSafety.isHazard(human, to.below()))) return Reason.HAZARD;
        double drop = Math.max(0, from.getY() - to.getY());
        if (drop == 0 || human.level().getFluidState(to).is(FluidTags.WATER)
                || human.level().getBlockState(to).is(net.minecraft.tags.BlockTags.CLIMBABLE)) return Reason.SAFE;
        return HumanFallPolicy.accepts(human.getHealth(), drop, human.expectedRawFallDamage((float) drop))
                ? Reason.SAFE : Reason.EXCESSIVE_DROP;
    }

    public static boolean safePath(Human human, Path path) {
        return assessPath(human, path).safe();
    }

    /** Pure assessment: speculative A-star candidates do not mutate failure memory. */
    public static Assessment assessPath(Human human, Path path) {
        if (path == null || path.isDone()) return new Assessment(Reason.UNAVAILABLE, human.blockPosition(), human.blockPosition());
        BlockPos previous = human.blockPosition();
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            BlockPos next = path.getNodePos(index);
            Reason reason = transitionReason(human, previous, next);
            if (reason != Reason.SAFE) return new Assessment(reason, previous, next);
            previous = next;
        }
        return Assessment.CLEAR;
    }

    /** Check every quarter block and the full foot width, not just a distant endpoint. */
    public static boolean safeStep(Human human, Vec3 offset) {
        return assessStep(human, offset).safe();
    }

    public static Assessment assessStep(Human human, Vec3 offset) {
        if (human.isInLava()) return Assessment.CLEAR; // Preserve emergency escape.
        double length = offset.horizontalDistance();
        if (!Double.isFinite(length) || !Double.isFinite(offset.y) || length > 4.0D)
            return new Assessment(Reason.UNAVAILABLE, human.blockPosition(), human.blockPosition());
        int count = Math.max(1, Mth.ceil(length / 0.25D));
        BlockPos previous = human.blockPosition();
        for (int i = 1; i <= count; i++) {
            Vec3 point = human.position().add(offset.scale((double) i / count));
            Reason reason = footprintReason(human, point);
            BlockPos next = BlockPos.containing(point);
            if (reason != Reason.SAFE) {
                if (previous.equals(next) && length > 0.01D)
                    next = BlockPos.containing(point.add(offset.multiply(1, 0, 1).normalize()));
                return new Assessment(reason, previous, next);
            }
            previous = next;
        }
        return Assessment.CLEAR;
    }

    public static boolean safeCombatJump(Human human) {
        if (human.isInWater() || human.isInLava() || human.onClimbable()) return true;
        Vec3 direction = human.getDeltaMovement().multiply(1, 0, 1);
        var path = human.getNavigation().getPath();
        if (path != null && !path.isDone())
            direction = Vec3.atBottomCenterOf(path.getNextNodePos()).subtract(human.position()).multiply(1, 0, 1);
        if (direction.horizontalDistanceSqr() < 0.001D) direction = human.getLookAngle().multiply(1, 0, 1);
        var jump = human.getEffect(net.minecraft.world.effect.MobEffects.JUMP);
        double velocity = 0.42D + (jump == null ? 0 : 0.1D * (jump.getAmplifier() + 1));
        double apex = velocity * velocity / 0.16D + 0.5D;
        return safeStep(human, direction.normalize().scale(1.5D).add(0, apex, 0));
    }

    private static Reason footprintReason(Human human, Vec3 point) {
        double half = Math.max(0.05D, human.getBbWidth() * 0.5D - 0.02D);
        int supported = 0;
        for (int sample = 0; sample < 5; sample++) {
            double x = point.x + (sample == 0 ? 0 : (sample <= 2 ? -half : half));
            double z = point.z + (sample == 0 ? 0 : ((sample & 1) == 0 ? -half : half));
            double landing = landingHeight(human, x, point.y, z);
            if (Double.isNaN(landing)) return Reason.UNAVAILABLE;
            if (landing == Double.NEGATIVE_INFINITY) return Reason.HAZARD;
            if (landing == Double.POSITIVE_INFINITY) { if (sample == 0) return Reason.UNSUPPORTED; else continue; }
            double drop = Math.max(0, point.y - landing);
            if (!HumanFallPolicy.accepts(human.getHealth(), drop, human.expectedRawFallDamage((float) drop))) return Reason.EXCESSIVE_DROP;
            supported++;
        }
        return supported >= 3 ? Reason.SAFE : Reason.UNSUPPORTED;
    }

    private static double landingHeight(Human human, double x, double feetY, double z) {
        int top = Mth.floor(feetY + 1.1D);
        for (int y = top; y >= Mth.floor(feetY) - HumanFallPolicy.MAX_PLANNED_DROP - 1; y--) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!human.level().hasChunkAt(pos) || !human.level().getWorldBorder().isWithinBounds(pos)) return Double.NaN;
            if (RetreatPathSafety.isHazard(human, pos)
                    && !pos.equals(human.blockPosition()) && !pos.equals(human.blockPosition().below())) return Double.NEGATIVE_INFINITY;
            if (human.level().getFluidState(pos).is(FluidTags.WATER)) return Math.min(feetY, y + 1.0D);
            var shape = human.level().getBlockState(pos).getCollisionShape(human.level(), pos);
            double best = Double.NEGATIVE_INFINITY;
            for (var box : shape.toAabbs()) {
                double height = y + box.maxY;
                if (x >= pos.getX() + box.minX && x <= pos.getX() + box.maxX
                        && z >= pos.getZ() + box.minZ && z <= pos.getZ() + box.maxZ
                        && height <= feetY + 1.1D) best = Math.max(best, height);
            }
            if (Double.isFinite(best)) return best;
        }
        return Double.POSITIVE_INFINITY;
    }

    public static Vec3 guardTravel(Human human, Vec3 input) {
        if (human.level().isClientSide || !human.isEffectiveAi() || human.isInLava()
                || human.onClimbable() || (!human.onGround() && !human.isInWater() && human.getDeltaMovement().y < -0.08D)) return input;
        Vec3 motionBefore = human.getDeltaMovement();
        boolean hasInput = input.horizontalDistanceSqr() >= 1.0E-6D;
        // Stopping a goal does not erase its previous horizontal momentum.
        // External knockback remains physical and must not gain an invisible brake.
        if (!hasInput && (human.hurtTime > 0 || motionBefore.horizontalDistanceSqr() < 0.0025D)) return input;
        double yaw = Math.toRadians(human.getYRot());
        Vec3 direction = hasInput
                ? new Vec3(input.x * Math.cos(yaw) - input.z * Math.sin(yaw), 0,
                        input.z * Math.cos(yaw) + input.x * Math.sin(yaw))
                : motionBefore.multiply(1, 0, 1);
        double ahead = Math.min(4.0D, Math.max(0.9D, motionBefore.horizontalDistance() * 3.0D + 0.5D));
        Assessment inputSafety = assessStep(human, direction.normalize().scale(pathLookahead(human, direction, ahead)));
        // A new sideways/backward command does not instantly redirect velocity.
        // Check the old trajectory too, unless it came from an external hit.
        Assessment momentumSafety = human.hurtTime > 0 || motionBefore.horizontalDistanceSqr() < 0.0025D
                ? Assessment.CLEAR : assessStep(human, motionBefore.multiply(1, 0, 1).normalize()
                        .scale(pathLookahead(human, motionBefore, ahead)));
        if (inputSafety.safe() && momentumSafety.safe()) return input;
        CombatTerrainNavigation.blocked(human, inputSafety.safe() ? momentumSafety : inputSafety);
        human.setZza(0); human.setXxa(0);
        if (human.hurtTime == 0) { // Do not turn the guard into knockback immunity.
            var motion = human.getDeltaMovement();
            human.setDeltaMovement(0, motion.y, 0);
        }
        human.getNavigation().stop();
        return new Vec3(0, input.y, 0);
    }

    /** Do not extrapolate a straight input beyond an imminent path corner into a cliff. */
    private static double pathLookahead(Human human, Vec3 direction, double ahead) {
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone()) return ahead;
        Vec3 next = path.getNextEntityPos(human).subtract(human.position()).multiply(1, 0, 1);
        Vec3 horizontal = direction.multiply(1, 0, 1);
        double distance = next.horizontalDistance();
        if (distance < .05 || horizontal.horizontalDistanceSqr() < .0001
                || horizontal.normalize().dot(next.normalize()) < .95) return ahead;
        // Still inspect at least the next actual tick's movement. Sharp turns may need braking.
        double immediate = Math.max(.25, human.getDeltaMovement().horizontalDistance());
        return Math.min(ahead, Math.max(immediate, distance));
    }
}
