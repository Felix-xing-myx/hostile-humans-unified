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

    public static boolean safeTransition(Human human, BlockPos from, BlockPos to) {
        if (!human.level().hasChunkAt(to) || !human.level().getWorldBorder().isWithinBounds(to)) return false;
        if (!human.isInLava() && (RetreatPathSafety.isHazard(human, to)
                || RetreatPathSafety.isHazard(human, to.above()) || RetreatPathSafety.isHazard(human, to.below()))) return false;
        double drop = Math.max(0, from.getY() - to.getY());
        if (drop == 0 || human.level().getFluidState(to).is(FluidTags.WATER)
                || human.level().getBlockState(to).is(net.minecraft.tags.BlockTags.CLIMBABLE)) return true;
        return HumanFallPolicy.accepts(human.getHealth(), drop, human.expectedRawFallDamage((float) drop));
    }

    public static boolean safePath(Human human, Path path) {
        if (path == null || path.isDone()) return false;
        BlockPos previous = human.blockPosition();
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            BlockPos next = path.getNodePos(index);
            if (!safeTransition(human, previous, next)) return false;
            previous = next;
        }
        return true;
    }

    /** Check every quarter block and the full foot width, not just a distant endpoint. */
    public static boolean safeStep(Human human, Vec3 offset) {
        if (human.isInLava()) return true; // Emergency lava escape must remain able to leave its start cell.
        double length = offset.horizontalDistance();
        if (!Double.isFinite(length) || length > 4.0D) return false;
        int count = Math.max(1, Mth.ceil(length / 0.25D));
        for (int i = 1; i <= count; i++) {
            Vec3 point = human.position().add(offset.scale((double) i / count));
            if (!safeFootprint(human, point)) return false;
        }
        return true;
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

    private static boolean safeFootprint(Human human, Vec3 point) {
        double half = Math.max(0.05D, human.getBbWidth() * 0.5D - 0.02D);
        int supported = 0;
        for (int sample = 0; sample < 5; sample++) {
            double x = point.x + (sample == 0 ? 0 : (sample <= 2 ? -half : half));
            double z = point.z + (sample == 0 ? 0 : ((sample & 1) == 0 ? -half : half));
            double landing = landingHeight(human, x, point.y, z);
            if (Double.isNaN(landing)) return false; // Hazard or unloaded terrain.
            if (Double.isInfinite(landing)) { if (sample == 0) return false; else continue; }
            double drop = Math.max(0, point.y - landing);
            if (!HumanFallPolicy.accepts(human.getHealth(), drop, human.expectedRawFallDamage((float) drop))) return false;
            supported++;
        }
        return supported >= 3;
    }

    private static double landingHeight(Human human, double x, double feetY, double z) {
        int top = Mth.floor(feetY + 1.1D);
        for (int y = top; y >= Mth.floor(feetY) - HumanFallPolicy.MAX_PLANNED_DROP - 1; y--) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!human.level().hasChunkAt(pos) || !human.level().getWorldBorder().isWithinBounds(pos)) return Double.NaN;
            if (RetreatPathSafety.isHazard(human, pos)
                    && !pos.equals(human.blockPosition()) && !pos.equals(human.blockPosition().below())) return Double.NaN;
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
        double ahead = Math.min(2.0D, Math.max(0.9D, human.getDeltaMovement().horizontalDistance() * 3.0D + 0.5D));
        if (safeStep(human, direction.normalize().scale(ahead))) return input;
        human.setZza(0); human.setXxa(0);
        if (human.hurtTime == 0) { // Do not turn the guard into knockback immunity.
            var motion = human.getDeltaMovement();
            human.setDeltaMovement(0, motion.y, 0);
        }
        human.getNavigation().stop();
        return new Vec3(0, input.y, 0);
    }
}
