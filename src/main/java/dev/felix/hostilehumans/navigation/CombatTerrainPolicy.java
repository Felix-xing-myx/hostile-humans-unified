package dev.felix.hostilehumans.navigation;

/** Geometry-independent decisions shared by ordinary pursuit, ranged movement and defense. */
final class CombatTerrainPolicy {
    static final int STALL_TICKS = 40;
    static final int MEMORY_TICKS = 100;
    static final int FAILURE_COOLDOWN_TICKS = 40;
    static final int DEFERRED_COOLDOWN_TICKS = 10;
    static final int[] RADII = {8, 16, 24};
    private CombatTerrainPolicy() {}

    static boolean partialUseful(double endDisplacementSqr, double oldDistanceSqr, double endDistanceSqr,
                                 boolean visited) {
        return !visited && endDisplacementSqr >= 4 && endDistanceSqr + 1 < oldDistanceSqr;
    }

    static boolean needsAlternative(int failures, int stalledTicks, boolean vetoed) {
        return vetoed || failures >= 2 || stalledTicks >= STALL_TICKS;
    }

    /** Empty geometry has not consumed an actual search attempt. */
    static boolean completedSearch(int work) { return work > 0; }

    static boolean holdFire(boolean canFire, boolean terrainProblem) { return canFire && terrainProblem; }

    static boolean meleeAllowed(boolean inReach, boolean completeSafeApproach) {
        return inReach || completeSafeApproach;
    }
}
