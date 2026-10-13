package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
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

    static Vec3 gunOrigin(LivingEntity shooter) {
        return shooter.position().add(shooter.xo, shooter.yo, shooter.zo).scale(0.5D)
                .add(0, shooter.getEyeHeight(), 0);
    }

    static Vec3 gunAim(Vec3 origin, ItemStack weapon, LivingEntity target) {
        Vec3 aim = GunSupport.get().shotTargetPoint(weapon, target);
        double speed = GunSupport.get().projectileSpeed(weapon);
        if (speed <= 0) return aim;
        double ticks = Mth.clamp(origin.distanceTo(aim) / speed, 0, 8);
        return aim.add(target.getDeltaMovement().scale(ticks))
                .add(0, 0.5D * GunSupport.get().projectileGravity(weapon) * ticks * ticks, 0);
    }

    public static boolean canShoot(Human human, LivingEntity target) {
        if (human.level().isClientSide || target == null || !target.isAlive()
                || target.level() != human.level()) return false;
        ItemStack weapon = RangedWeaponCustody.preferredForClearance(human);
        Vec3 feet = human.position();
        Vec3 oldFeet = new Vec3(human.xo, human.yo, human.zo);
        Sample sample = SAMPLES.get(human);
        if (sample != null && sample.tick() == human.tickCount && sample.target() == target
                && sample.weapon() == weapon && sample.feet().equals(feet)
                && sample.oldFeet().equals(oldFeet) && sample.targetFeet().equals(target.position())
                && sample.motion().equals(target.getDeltaMovement())) return sample.clear();
        boolean clear = human.getSensing().hasLineOfSight(target)
                && lane(human, target, feet, true);
        SAMPLES.put(human, new Sample(human.tickCount, target, weapon, feet, oldFeet,
                target.position(), target.getDeltaMovement(), clear));
        return clear;
    }

    /** Bounded firing-position search already budgets the number of candidates. */
    static boolean fromPosition(Human human, Vec3 feet, LivingEntity target) {
        return clearSegment(human, feet.add(0, human.getEyeHeight(), 0), target.getEyePosition())
                && lane(human, target, feet, false);
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
            // Native Scguns owns leading. Only the TaCZ-style aiming path adds
            // our prediction; do not apply a second lead to Scguns' firing angle.
            double aimingSpeed = GunSupport.get().projectileSpeed(weapon);
            double ticks = aimingSpeed > 0 ? Mth.clamp(origin.distanceTo(aim) / aimingSpeed, 0, 8) : 0;
            Vec3 predicted = aim.add(target.getDeltaMovement().scale(ticks));
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
            direction = HumanGunner.solveArrowBallistics(origin, targetPoint, target.getDeltaMovement(), speed);
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
