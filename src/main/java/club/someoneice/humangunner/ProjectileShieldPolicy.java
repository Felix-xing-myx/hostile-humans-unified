package club.someoneice.humangunner;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps predictive projectile blocks from repeatedly pre-empting ranged attacks. */
final class ProjectileShieldPolicy {
    private static final double RANGED_IMPACT_WINDOW_TICKS = 10.0D;
    private static final int MAX_RANGED_GUARD_WINDOW_TICKS = 12;
    static final int MAX_PREDICTION_TICKS = 12;

    private ProjectileShieldPolicy() {
    }

    /** Full 120 degree cone, including pitch. */
    static boolean insideReactionView(Vec3 look, Vec3 offset) {
        double product = look.lengthSqr() * offset.lengthSqr();
        return Double.isFinite(product) && look.lengthSqr() > 1.0E-8
                && (offset.lengthSqr() < 1.0E-8 || look.dot(offset) >= Math.sqrt(product) * .5D);
    }
    static boolean shouldGuardRangedUser(double ticksToImpact) {
        return Double.isFinite(ticksToImpact)
                && ticksToImpact >= 0.0D
                && ticksToImpact <= RANGED_IMPACT_WINDOW_TICKS;
    }

    static int guardWindowTicks(boolean rangedCombatant, double ticksToImpact) {
        if (!rangedCombatant) {
            return Double.isFinite(ticksToImpact) && ticksToImpact >= 0.0D
                    && ticksToImpact <= MAX_PREDICTION_TICKS
                    ? Math.max(6, (int) Math.ceil(ticksToImpact) + 2) : 0;
        }
        if (!shouldGuardRangedUser(ticksToImpact)) {
            return 0;
        }
        return Math.min(MAX_RANGED_GUARD_WINDOW_TICKS,
                Math.max(6, (int) Math.ceil(ticksToImpact) + 2));
    }

    /** Swept, drag-aware trajectory; proximity alone never counts as impact. */
    static double impactTicks(Vec3 position, Vec3 velocity, AABB body, Vec3 defenderMotion,
                              double drag, double gravity) {
        return impactTicks(position, velocity, body, defenderMotion,
                new ShieldProjectileBackend.Motion(drag, gravity, true));
    }
    static double impactTicks(Vec3 position, Vec3 velocity, AABB body, Vec3 defenderMotion,
                              ShieldProjectileBackend.Motion model) {
        AABB collision = body.inflate(0.15D, 0.1D, 0.15D);
        for (int tick = 0; tick < Math.min(MAX_PREDICTION_TICKS, model.horizon()); tick++) {
            // Relative motion handles an advancing/side-stepping defender.
            Vec3 relativeStart = position.subtract(defenderMotion.scale(tick));
            Vec3 next = position.add(velocity);
            Vec3 relativeEnd = next.subtract(defenderMotion.scale(tick + 1));
            var hit = collision.clip(relativeStart, relativeEnd);
            if (collision.contains(relativeStart)) return tick;
            if (hit.isPresent()) {
                double length = relativeStart.distanceTo(relativeEnd);
                return tick + (length < 1.0E-9D ? 0.0D : relativeStart.distanceTo(hit.get()) / length);
            }
            position = next;
            velocity = velocity.add(model.acceleration()).scale(model.drag()).add(0.0D, -model.gravity(), 0.0D);
        }
        return Double.NaN;
    }
}
