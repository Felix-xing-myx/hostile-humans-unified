package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.JsonObject;

/** Immutable, server-side explicit-route limits; does not change senses or FOLLOW_RANGE. */
public record NavigationSearchConfig(boolean enabled, int normalRange, int extendedRange, int longRange,
        int normalNodes, int extendedNodes, int longNodes, int remoteUnits, int failureTicks, int maximumFailureTicks,
        int cacheEntries, int validationTicks, double retargetDistance) {
    public static NavigationSearchConfig parse(JsonObject data) {
        int normalRange = integer(data, "normal_range", 48, 8, 128);
        int extendedRange = Math.max(normalRange, integer(data, "extended_range", 80, 8, 192));
        int longRange = Math.max(extendedRange, integer(data, "long_range", 128, 8, 256));
        int normalNodes = integer(data, "normal_nodes", 1536, 128, 4096);
        int extendedNodes = Math.max(normalNodes, integer(data, "extended_nodes", 2048, 128, 8192));
        int longNodes = Math.max(extendedNodes, integer(data, "long_nodes", 4096, 128, 16384));
        int failure = integer(data, "failure_ticks", 200, 20, 2400);
        // One remote unit reserves 1024 potential A* visits. Always admit the largest configured job.
        int units = Math.max((longNodes + 1023) / 1024, integer(data, "remote_units_per_tick", 8, 1, 64));
        return new NavigationSearchConfig(UnifiedConfig.bool(data, "enabled", true), normalRange, extendedRange,
                longRange, normalNodes, extendedNodes, longNodes, units, failure,
                Math.max(failure, integer(data, "maximum_failure_ticks", 1200, 20, 12000)),
                integer(data, "failure_cache_entries", 16, 1, 64), integer(data, "validation_ticks", 20, 1, 100),
                UnifiedConfig.number(data,"retarget_distance",4,1,16));
    }
    private static int integer(JsonObject data, String key, int fallback, int min, int max) {
        return (int)UnifiedConfig.number(data, key, fallback, min, max);
    }
    boolean sameSearchLimits(NavigationSearchConfig other) {
        return other != null && enabled == other.enabled
                && normalRange == other.normalRange && extendedRange == other.extendedRange && longRange == other.longRange
                && normalNodes == other.normalNodes && extendedNodes == other.extendedNodes && longNodes == other.longNodes;
    }

    public int range(int stage) { return stage == 0 ? normalRange : stage == 1 ? extendedRange : longRange; }
    public int nodes(int stage) { return stage == 0 ? normalNodes : stage == 1 ? extendedNodes : longNodes; }
}
