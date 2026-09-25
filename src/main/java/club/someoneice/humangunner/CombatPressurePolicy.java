package club.someoneice.humangunner;

/** Pure combat decisions shared by the live goal and behavior regression tests. */
final class CombatPressurePolicy {
    static final int MINIMUM_EFFECTIVE_BLOCK_TICKS = 6;
    static final int QUIET_RELEASE_TICKS = 10;
    static final int RECENT_HIT_TICKS = 6;
    static final int GUNFIRE_MEMORY_TICKS = 40;
    static final int CROWD_THREAT_COUNT = 3;
    static final int CROWD_PRESSURE_MEMORY_TICKS = 20;

    private CombatPressurePolicy() {
    }

    static boolean shouldOpenCounterWindow(
            int heldTicks, int quietTicks, boolean canCounterFromCurrentRange
    ) {
        return heldTicks >= MINIMUM_EFFECTIVE_BLOCK_TICKS
                && (canCounterFromCurrentRange || quietTicks >= QUIET_RELEASE_TICKS);
    }

    static boolean isRecentHit(int currentTick, int hurtTick) {
        return hurtTick >= 0 && currentTick >= hurtTick
                && currentTick - hurtTick <= RECENT_HIT_TICKS;
    }

    static boolean isRecentGunfire(int currentTick, int gunfireUntil) {
        return gunfireUntil >= currentTick
                && gunfireUntil - currentTick >= GUNFIRE_MEMORY_TICKS - RECENT_HIT_TICKS;
    }

    static boolean predictsIncomingMelee(
            double distanceSqr, boolean swinging, boolean hasIntent,
            boolean facingDefender, double approachSpeed
    ) {
        if (distanceSqr > 12.25D || !hasIntent || !facingDefender
                || approachSpeed <= -0.025D) {
            return false;
        }
        return swinging || (distanceSqr <= 9.0D && approachSpeed >= 0.035D);
    }

    static boolean shouldReleaseForPursuit(
            int heldTicks, int quietTicks, boolean meleeThreat,
            double distanceSqr, double approachSpeed
    ) {
        return heldTicks >= MINIMUM_EFFECTIVE_BLOCK_TICKS
                && quietTicks >= 4
                && meleeThreat
                && (distanceSqr > 16.0D
                || (distanceSqr > 6.25D && approachSpeed <= -0.025D));
    }

    static boolean shouldPrioritizeGunnerRetreat(boolean ownsGun, int closeThreatCount) {
        return ownsGun && closeThreatCount >= CROWD_THREAT_COUNT;
    }

    static boolean shouldUseCrowdCounterfire(
            boolean ownsGun, boolean crowdPressureActive, boolean hasLineOfSight, double distanceSqr
    ) {
        return ownsGun && crowdPressureActive && hasLineOfSight && distanceSqr <= 784.0D;
    }
}
