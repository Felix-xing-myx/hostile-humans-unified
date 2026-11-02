package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.navigation.HumanTerrainSafety;
import dev.felix.hostilehumans.soldier.OrderDistances;
import dev.felix.hostilehumans.soldier.SoldierOrder;

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
        double preferred = preferredDistance(human,target);
        double tooClose = dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.tooClose(reach,
                BetterCombatMeleeCombat.hasWeaponProfile(human));
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
        return dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.preferred(reach);
    }
    public static double preferredDistance(Human human,LivingEntity target) {
        if(target==null)return 0;
        double reach=MeleeCombatRange.reach(human,target);
        if(!BetterCombatMeleeCombat.hasWeaponProfile(human))
            return dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.preferred(reach, false);
        Vec3 away=target.position().subtract(human.position()).multiply(1,0,1);
        if(away.lengthSqr()<1.e-6)return dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.preferred(reach, true);
        away=away.normalize();
        double outward=Math.max(target.getDeltaMovement().dot(away),
                target.position().subtract(new Vec3(target.xo,target.yo,target.zo)).dot(away));
        int pending=human.hhu$pendingMeleeTicks(target);
        int upswing=pending>=0?pending:BetterCombatMeleeCombat.upswingTicks(human);
        return dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.chasePreferred(reach,outward,upswing,true);
    }
    /** Shared by spacing, pursuit refresh and sprint; a hit at this instant is not enough reserve. */
    public static boolean needsChaseBuffer(Human human,LivingEntity target) {
        if(target==null||!BetterCombatMeleeCombat.hasWeaponProfile(human))return false;
        double normal=dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacingPolicy.preferred(MeleeCombatRange.reach(human,target),true);
        double preferred=preferredDistance(human,target);
        return preferred<normal-1.e-6&&human.distanceTo(target)>preferred;
    }

    private static boolean tryStep(Human human, Vec3 step) {
        BlockPos feet = BlockPos.containing(human.position().add(step));
        BlockPos floor = feet.below();
        var command = OrderDistances.context(human);
        if (command.constrained() && (!command.available() || !command.insideCombat(human.position().add(step)))) return false;
        if (!HumanTerrainSafety.safeStep(human, step) || !human.level().hasChunkAt(feet)
                || !human.level().getBlockState(floor).isFaceSturdy(human.level(), floor, Direction.UP)
                || !human.level().getFluidState(feet).isEmpty()
                || !human.level().noCollision(human, human.getBoundingBox().move(step))) return false;
        double yaw = Math.toRadians(human.getYRot());
        Vec3 direction = step.normalize();
        float forward = (float) (direction.x * -Math.sin(yaw) + direction.z * Math.cos(yaw));
        float right = (float) (direction.x * Math.cos(yaw) + direction.z * Math.sin(yaw));
        human.getNavigation().stop();
        human.strafeMelee(forward * 0.5F, right * 0.5F);
        return true;
    }
}
