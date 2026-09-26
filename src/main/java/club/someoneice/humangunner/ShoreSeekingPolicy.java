package club.someoneice.humangunner;

/** Pure decisions shared by water-exit assistance and combat movement. */
public final class ShoreSeekingPolicy {
    // Adaptive combat/retreat (priority -8 and below) wins; idle orders and
    // wandering (priority -6 and above) yield while an idle Human exits water.
    public static final int GOAL_PRIORITY = -7;
    /** Hard budget for synchronous shoreline candidate inspection per search. */
    public static final int MAX_SHORE_BLOCK_PROBES_PER_SEARCH = 64;
    /** Two destinations per search; navigation may test ground and water for each. */
    public static final int MAX_SHORE_PATH_PROBES_PER_SEARCH = 2;
    private static final float SUBMERGED_WATER_PATH_MALUS = 8.0F;
    private static final float ACTIVE_SHORE_WATER_PATH_MALUS = 16.0F;
    private static final float COMBAT_WATER_PATH_MALUS = 0.0F;
    private static final int MAX_SAFE_SHORE_DETOUR_NODES = 6;

    private ShoreSeekingPolicy() {
    }

    public static boolean shouldAttemptShore(boolean serverSide, boolean effectiveAi,
            boolean inWater, boolean inLava, boolean hasCombatTarget, boolean fleeing) {
        // Shore assistance is an idle fallback, never a competing combat goal.
        return serverSide && effectiveAi && inWater && !inLava
                && !hasCombatTarget && !fleeing;
    }

    public static boolean shouldSearchForShorePath(int currentTick, int nextSearchTick) {
        return currentTick >= nextSearchTick;
    }

    /** The ranged goal yields movement to shore navigation but keeps aiming and attacking. */
    public static boolean isShoreTransitionActive(boolean seekingShore, boolean transitionPending) {
        return seekingShore || transitionPending;
    }

    /** Combat approach goals must not replace the active route to dry ground. */
    public static boolean shouldMoveTowardCombatTarget(boolean seekingShore) {
        return !seekingShore;
    }

    /** Keep a direct bank-steering fallback when navigation has no usable path. */
    public static boolean shouldSteerTowardFallback(boolean seekingShore,
            boolean hasDryDestination, boolean navigationDone) {
        return seekingShore && hasDryDestination && navigationDone;
    }

    public static boolean shouldContinueSeekingShore(boolean inWater, boolean inLava,
            boolean hasCombatTarget, boolean fleeing) {
        // Yield immediately if combat or retreat takes ownership of movement.
        return inWater && !inLava && !hasCombatTarget && !fleeing;
    }

    public static boolean shouldPursueLowerWaterTarget(boolean seekingShore,
            boolean targetInWater, boolean targetIsLower, boolean fleeing, boolean catchingBreath) {
        // A lower underwater target may influence swimming only when there is
        // no stronger shore-exit, retreat, or breath-recovery objective.
        return !seekingShore && targetInWater && targetIsLower && !fleeing && !catchingBreath;
    }

    public static float waterPathMalus(boolean inWater, boolean seekingShore,
            boolean hasCombatTarget) {
        // Combat paths must be able to choose a direct river crossing instead
        // of being routed around the entire body of water. Idle paths still
        // prefer land, and shore assistance strongly avoids looping in water.
        if (seekingShore) {
            return ACTIVE_SHORE_WATER_PATH_MALUS;
        }
        if (hasCombatTarget) {
            return COMBAT_WATER_PATH_MALUS;
        }
        if (!inWater) {
            return -1.0F;
        }
        return SUBMERGED_WATER_PATH_MALUS;
    }

    public static boolean shouldPreferSaferShorePath(int shortestPathNodes,
            int saferPathNodes, boolean increasesThreatDistance) {
        return increasesThreatDistance
                && saferPathNodes <= shortestPathNodes + MAX_SAFE_SHORE_DETOUR_NODES;
    }

    public static boolean shouldClearFleeingAfterCombatStop(boolean ownsFleeFlag,
            boolean shoreTransitionPending) {
        return ownsFleeFlag && !shoreTransitionPending;
    }

    public static boolean shouldReturnToOwnerAfterCombatStop(boolean retreatEnded,
            boolean shoreTransitionPending) {
        return retreatEnded && !shoreTransitionPending;
    }
}
