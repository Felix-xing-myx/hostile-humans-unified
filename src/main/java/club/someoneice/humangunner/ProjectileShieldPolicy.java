package club.someoneice.humangunner;

/** Keeps predictive projectile blocks from repeatedly pre-empting ranged attacks. */
final class ProjectileShieldPolicy {
    private static final double RANGED_IMPACT_WINDOW_TICKS = 2.5D;
    private static final int MAX_RANGED_GUARD_WINDOW_TICKS = 3;

    private ProjectileShieldPolicy() {
    }

    static boolean shouldGuardRangedUser(double ticksToImpact) {
        return Double.isFinite(ticksToImpact)
                && ticksToImpact >= 0.0D
                && ticksToImpact <= RANGED_IMPACT_WINDOW_TICKS;
    }

    static int guardWindowTicks(boolean rangedCombatant, double ticksToImpact) {
        if (!rangedCombatant) {
            return 32;
        }
        if (!shouldGuardRangedUser(ticksToImpact)) {
            return 0;
        }
        return Math.min(MAX_RANGED_GUARD_WINDOW_TICKS,
                Math.max(1, (int) Math.ceil(ticksToImpact) + 1));
    }
}
