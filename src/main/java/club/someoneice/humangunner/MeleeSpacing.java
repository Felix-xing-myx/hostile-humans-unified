package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A short, path-checked spacing correction; never owns fleeing or water combat. */
public final class MeleeSpacing {
    private MeleeSpacing() {}

    /** Returns true when ordinary pursuit should yield to melee spacing. */
    public static boolean control(Human human, LivingEntity target) {
        if (human.isFleeing || SoldierOrder.isHoldingPosition(human)
                || human.isInWater() || human.isInLava()
                || (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem()))
                || !human.getSensing().hasLineOfSight(target)) return false;

        double reach = MeleeCombatRange.reach(human, target);
        double distance = human.distanceTo(target);
        double preferred = preferredDistance(reach);
        double tooClose = dev.felix.hostilehumans.core.MeleeSpacingPolicy.tooClose(reach);
        if (distance > preferred) return false;
        // A visible target on another step may be outside the actual oriented
        // weapon hitbox. Never stop pursuit merely because its feet are near.
        if (!MeleeCombatRange.canStrike(human, target)) return false;
        if (distance >= tooClose) {
            int sinceHit = human.tickCount - human.getLastHurtByMobTimestamp();
            if (human.getLastHurtByMob() != null && sinceHit >= 0 && sinceHit <= 10) {
                // Being inside a valid hit band is not a reason to freeze
                // after impact. A short, collision-checked lateral adjustment
                // keeps the target in reach and leaves attack cooldown intact.
                Vec3 toward = target.position().subtract(human.position()).multiply(1.0D, 0.0D, 1.0D);
                if (toward.lengthSqr() > 1.0E-4D) {
                    Vec3 side = new Vec3(-toward.z, 0.0D, toward.x).normalize()
                            .scale(Math.min(0.35D, reach * 0.15D));
                    if ((human.getId() & 1) != 0) side = side.scale(-1.0D);
                    // Do not persist a stationary spacing claim when neither
                    // flank is usable: let ordinary pursuit retry its route.
                    return tryStep(human, side) || tryStep(human, side.scale(-1.0D));
                }
                return false;
            }
            human.getNavigation().stop();
            return true;
        }
        Vec3 away = human.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-4D) {
            // Overlapping centers need a deterministic escape direction,
            // not an indefinite navigation stop waiting for an external push.
            away = new Vec3(Math.cos(Math.toRadians(human.getYRot())), 0.0D,
                    Math.sin(Math.toRadians(human.getYRot())));
        }
        // Check the next step, not a path behind the mob: a retreat path would
        // rotate the whole body away from its opponent. STRAFE keeps the torso
        // and the ordinary walking legs facing the fight while stepping back.
        Vec3 step = away.normalize().scale(0.8D);
        if (tryStep(human, step)) return true;
        // A wall or another soldier behind us must not permanently block
        // melee movement. Try both flanks, then release ordinary pursuit.
        Vec3 side = new Vec3(-step.z, 0.0D, step.x);
        if ((human.getId() & 1) != 0) side = side.scale(-1.0D);
        return tryStep(human, side) || tryStep(human, side.scale(-1.0D));
    }

    public static double preferredDistance(double reach) {
        return dev.felix.hostilehumans.core.MeleeSpacingPolicy.preferred(reach);
    }

    private static boolean tryStep(Human human, Vec3 step) {
        BlockPos feet = BlockPos.containing(human.position().add(step));
        BlockPos floor = feet.below();
        if (!human.level().hasChunkAt(feet)
                || !human.level().getBlockState(floor).isFaceSturdy(human.level(), floor, Direction.UP)
                || !human.level().getFluidState(feet).isEmpty()
                || !human.level().noCollision(human, human.getBoundingBox().move(step))) return false;
        double yaw = Math.toRadians(human.getYRot());
        Vec3 direction = step.normalize();
        float forward = (float) (direction.x * -Math.sin(yaw) + direction.z * Math.cos(yaw));
        float right = (float) (direction.x * Math.cos(yaw) + direction.z * Math.sin(yaw));
        human.getNavigation().stop();
        human.strafeMelee(forward * 0.6F, right * 0.6F);
        return true;
    }
}
