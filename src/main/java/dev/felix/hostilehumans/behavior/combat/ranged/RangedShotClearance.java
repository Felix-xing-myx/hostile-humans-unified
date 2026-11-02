package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.gun.GunTargetMotion;
import dev.felix.hostilehumans.behavior.combat.gun.GunTrackingPolicy;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat;
import dev.felix.hostilehumans.perception.HumanPerception;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-side nominal shot lane. Awareness LOS remains a separate condition.
 * Movement candidates and final firing permission must use the same geometry.
 * Does not roll spread, consume ammo, spawn projectiles or change charge progress.
 */
public final class RangedShotClearance {
    private static final Map<Human, Sample> SAMPLES = new WeakHashMap<>();
    private record Sample(int tick, LivingEntity target, ItemStack weapon, Vec3 feet,
                          Vec3 oldFeet, Vec3 targetFeet, Vec3 motion, boolean clear) { }
    private RangedShotClearance() { }

    public static Vec3 gunOrigin(LivingEntity shooter) {
        return shooter.position().add(shooter.xo, shooter.yo, shooter.zo).scale(0.5D)
                .add(0, shooter.getEyeHeight(), 0);
    }

    public static Vec3 gunAim(Vec3 origin, ItemStack weapon, LivingEntity target) {
        Vec3 aim = GunSupport.get().shotTargetPoint(weapon, target);
        double speed = GunSupport.get().projectileSpeed(weapon);
        if (speed <= 0) return aim;
        Vec3 motion = GunTargetMotion.velocity(target);
        double gravity = GunSupport.get().projectileGravity(weapon);
        double ticks = gunFlightTicks(origin, aim, motion, speed, gravity);
        return aim.add(motion.scale(ticks)).add(0, 0.5D * gravity * ticks * ticks, 0);
    }

    private static double gunFlightTicks(Vec3 origin, Vec3 aim, Vec3 motion, double speed, double gravity) {
        Vec3 delta = aim.subtract(origin);
        return GunTrackingPolicy.flightTicks(delta.x, delta.y, delta.z,
                motion.x, motion.y, motion.z, speed, gravity);
    }

    public static boolean canShoot(Human human, LivingEntity target) {
        if (human.level().isClientSide || target == null || !target.isAlive()
                || target.level() != human.level() || !HumanPerception.canTarget(human,target)) return false;
        ItemStack weapon = RangedWeaponCustody.preferredForClearance(human);
        Vec3 feet = human.position();
        Vec3 oldFeet = new Vec3(human.xo, human.yo, human.zo);
        Sample sample = SAMPLES.get(human);
        if (sample != null && sample.tick() == human.tickCount && sample.target() == target
                && sample.weapon() == weapon && sample.feet().equals(feet)
                && sample.oldFeet().equals(oldFeet) && sample.targetFeet().equals(target.position())
                && sample.motion().equals(GunTargetMotion.velocity(target))) return sample.clear();
        boolean clear = lane(human, target, feet, true);
        SAMPLES.put(human, new Sample(human.tickCount, target, weapon, feet, oldFeet,
                target.position(), GunTargetMotion.velocity(target), clear));
        return clear;
    }

    /** Diagnostics only read the last existing sample; opening a page never casts a ray. */
    public static String debugState(Human human,LivingEntity target){
        Sample sample=SAMPLES.get(human);
        if(sample==null||sample.target()!=target)return "尚无当前目标的射界采样";
        return (sample.clear()?"最近采样通畅":"最近采样受阻")+" · "+Math.max(0,human.tickCount-sample.tick())+"tick前";
    }

    /** Bounded firing-position search already budgets the number of candidates. */
    static boolean fromPosition(Human human, Vec3 feet, LivingEntity target) {
        return HumanPerception.canTarget(human,target) && lane(human, target, feet, false);
    }

    private static boolean lane(Human human, LivingEntity target, Vec3 feet, boolean current) {
        ItemStack weapon = RangedWeaponCustody.preferredForClearance(human);
        if (GunSupport.get().isGun(weapon)) {
            // Both native backends interpolate the owner's previous/current feet
            // and add eye height. Cosmetic third-person muzzle offsets are not physics.
            Vec3 origin = (current ? gunOrigin(human) : feet.add(0, human.getEyeHeight(), 0))
                    .add(GunSupport.get().shotOriginOffset(weapon, human));
            Vec3 aim = GunSupport.get().shotTargetPoint(weapon, target);
            double speed = GunSupport.get().clearanceProjectileSpeed(weapon);
            if (speed <= 0) return clearSegment(human, origin, aim);
            // All opted-in gun backends share the exact same intercept for shots and lanes.
            // A zero aiming speed explicitly leaves prediction to an unadapted native backend.
            double aimingSpeed = GunSupport.get().projectileSpeed(weapon);
            Vec3 motion = GunTargetMotion.velocity(target);
            double ticks = aimingSpeed > 0 ? gunFlightTicks(origin, aim, motion, aimingSpeed,
                    GunSupport.get().projectileGravity(weapon)) : 0;
            Vec3 predicted = aim.add(motion.scale(ticks));
            double gravity = GunSupport.get().clearanceProjectileGravity(weapon);
            Vec3 direction = gunAim(origin, weapon, target).subtract(origin);
            return clearArc(human, origin, direction.normalize().scale(speed), predicted, 1, gravity);
        }
        // AbstractArrow(owner) starts 0.1 blocks below the eyes. Spartan's
        // supported bolts use the same owner constructor and shared ballistics.
        Vec3 origin = feet.add(0, human.getEyeHeight() - 0.1D, 0);
        Vec3 targetPoint = target.position().add(0, target.getBbHeight() * 0.85D, 0);
        boolean trident = weapon.getItem() instanceof TridentItem;
        double speed = trident ? 2.9D : SpartanRangedCompat.isHeavyCrossbow(weapon) ? 3.15D : 2.7D;
        Vec3 direction;
        if (trident) {
            double ticks = Mth.clamp(origin.distanceTo(targetPoint) / speed, 0, 12);
            targetPoint = targetPoint.add(target.getDeltaMovement().scale(ticks));
            direction = targetPoint.add(0, 0.025D * ticks * ticks, 0).subtract(origin);
        } else {
            direction = HumanRuntime.solveArrowBallistics(origin, targetPoint, target.getDeltaMovement(), speed);
        }
        return clearArc(human, origin, direction.normalize().scale(speed), targetPoint, 0.99D, 0.05D);
    }

    private static boolean clearArc(Human human, Vec3 origin, Vec3 velocity, Vec3 target,
                                    double drag, double gravity) {
        Vec3 axis = new Vec3(target.x - origin.x, 0, target.z - origin.z);
        double length = axis.length();
        if (length < 0.01D) return clearSegment(human, origin, target);
        axis = axis.scale(1 / length);
        Vec3 position = origin;
        // One collision segment per native tick, at most 60, matching the
        // existing arrow solver's envelope. Stop at the target plane, not behind it.
        for (int tick = 0; tick < 60; tick++) {
            Vec3 next = position.add(velocity);
            double remaining = length - position.subtract(origin).dot(axis);
            double advance = velocity.dot(axis);
            boolean reached = advance >= remaining;
            if (reached) next = position.add(velocity.scale(remaining / advance));
            if (!clearSegment(human, position, next)) return false;
            if (reached) return true;
            position = next;
            velocity = velocity.scale(drag).add(0, -gravity, 0);
        }
        return false;
    }

    private static boolean clearSegment(Human human, Vec3 from, Vec3 to) {
        return human.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, human)).getType() == HitResult.Type.MISS;
    }
}
