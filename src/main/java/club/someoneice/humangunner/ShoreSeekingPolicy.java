package club.someoneice.humangunner;

/** Pure decisions shared by water-exit movement and combat handoff. */
public final class ShoreSeekingPolicy {
    public static final int GOAL_PRIORITY = -40;
    private static final float SUBMERGED_WATER_PATH_MALUS = 8.0F;
    private static final float ACTIVE_SHORE_WATER_PATH_MALUS = 16.0F;
    private static final int MAX_SAFE_SHORE_DETOUR_NODES = 6;

    private ShoreSeekingPolicy() {
    }

    public static boolean shouldAttemptShore(boolean serverSide, boolean effectiveAi,
            boolean inWater, boolean inLava) {
        return serverSide && effectiveAi && inWater && !inLava;
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

    public static boolean shouldContinueSeekingShore(boolean inWater, boolean inLava) {
        // Keep the environmental movement lease while submerged even if one
        // search cycle cannot currently produce a reachable shore path.
        return inWater && !inLava;
    }

    public static boolean shouldPursueLowerWaterTarget(boolean seekingShore,
            boolean targetInWater, boolean targetIsLower, boolean fleeing, boolean catchingBreath) {
        // A lower underwater target may influence swimming only when there is
        // no stronger shore-exit, retreat, or breath-recovery objective.
        return !seekingShore && targetInWater && targetIsLower && !fleeing && !catchingBreath;
    }

    public static float waterPathMalus(boolean inWater, boolean seekingShore) {
        // Keep water traversable when it is unavoidable, but make all combat
        // paths prefer land and make shore-exit routes strongly avoid needless
        // loops through water.
        if (seekingShore) {
            return ACTIVE_SHORE_WATER_PATH_MALUS;
        }
        return inWater ? SUBMERGED_WATER_PATH_MALUS : -1.0F;
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
