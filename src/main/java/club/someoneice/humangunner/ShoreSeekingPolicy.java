package club.someoneice.humangunner;

import java.util.Map;
import java.util.WeakHashMap;

/** Pure decisions shared by water-exit assistance and combat movement. */
public final class ShoreSeekingPolicy {
    // Combat, water-loot pickup, and idle shore seeking must have distinct
    // MOVE priorities; a same-priority goal cannot reliably preempt another.
    public static final int COMBAT_GOAL_PRIORITY = -9;
    public static final int WATER_LOOT_GOAL_PRIORITY = -8;
    public static final int GOAL_PRIORITY = -7;
    /** Hard budget for synchronous shoreline candidate inspection per search. */
    public static final int MAX_SHORE_BLOCK_PROBES_PER_SEARCH = 64;
    /** Two destinations per search; navigation may test ground and water for each. */
    public static final int MAX_SHORE_PATH_PROBES_PER_SEARCH = 2;
    /** Pathfinding runs on the server thread; stagger Humans as well as probes. */
    public static final int MAX_SHORE_PATH_DISTANCE = 40;
    private static final Map<Object, Integer> LAST_SHORE_SEARCH_TICK = new WeakHashMap<>();
    private static final float SUBMERGED_WATER_PATH_MALUS = 8.0F;
    private static final float ACTIVE_SHORE_WATER_PATH_MALUS = 16.0F;
    private static final float COMBAT_WATER_PATH_MALUS = 0.0F;
    private static final int MAX_SAFE_SHORE_DETOUR_NODES = 6;
    /** Feet remain about half a block below the surface while idling afloat. */
    public static final double SURFACE_STANCE_DEPTH = 0.55D;

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

    public static boolean reserveShorePathSearch(Object server, int serverTick) {
        Integer lastTick = LAST_SHORE_SEARCH_TICK.get(server);
        if (lastTick != null && lastTick == serverTick) {
            return false;
        }
        LAST_SHORE_SEARCH_TICK.put(server, serverTick);
        return true;
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

    public static boolean shouldRiseTowardSurface(boolean eyesInWater,
            double feetToRealSurface) {
        return eyesInWater || feetToRealSurface > SURFACE_STANCE_DEPTH;
    }

    public static double surfaceAscentLimit(double feetToRealSurface, double ordinaryLimit) {
        return Math.min(ordinaryLimit,
                Math.max(0.0D, (feetToRealSurface - SURFACE_STANCE_DEPTH) * 0.5D));
    }

    public static boolean isNearRealSurfaceForShorePop(double feetToRealSurface) {
        return feetToRealSurface >= -0.1D && feetToRealSurface <= 1.25D;
    }

    /** One dry block above the water block, including partially filled flowing water. */
    public static double oneBlockShoreLandingLimit(double actualSurfaceY) {
        return Math.ceil(actualSurfaceY) + 1.0D;
    }

    public static boolean isSurfaceLootReachable(double surfaceY, double itemY) {
        return surfaceY - itemY <= 1.5D;
    }

    public static boolean shouldContinueSeekingShore(boolean inWater, boolean inLava,
            boolean hasCombatTarget, boolean fleeing) {
        // Yield immediately if combat or retreat takes ownership of movement.
        return inWater && !inLava && !hasCombatTarget && !fleeing;
    }

    public static float waterPathMalus(boolean inWater, boolean seekingShore,
            boolean hasCombatTarget, boolean pursuingWaterLoot) {
        // Combat paths must be able to choose a direct river crossing instead
        // of being routed around the entire body of water. Idle paths still
        // prefer land, and shore assistance strongly avoids looping in water.
        if (hasCombatTarget || pursuingWaterLoot) {
            return COMBAT_WATER_PATH_MALUS;
        }
        if (seekingShore) {
            return ACTIVE_SHORE_WATER_PATH_MALUS;
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
