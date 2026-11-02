package dev.felix.hostilehumans.navigation;

/** Deterministic costs, escalation and failed-scene reuse shared by explicit route consumers. */
public final class RouteSearchPolicy {
    private RouteSearchPolicy() {}
    public static int startStage(double horizontalDistance, int normalRange, int extendedRange) {
        return horizontalDistance <= normalRange ? 0 : horizontalDistance <= extendedRange ? 1 : 2;
    }
    public static int remoteCost(int nodeLimit) {
        if (nodeLimit < 1) throw new IllegalArgumentException("node limit must be positive");
        return (nodeLimit + 1023) / 1024;
    }
    public static int failureDelay(int failures, int initial, int maximum) {
        long delay = (long)initial << Math.max(0, Math.min(6, failures - 1));
        return (int)Math.min(maximum, delay);
    }
    public static boolean retry(int elapsed, int delay, int maximum, boolean moved, boolean changed) {
        return elapsed < 0 || moved || elapsed >= maximum || elapsed >= delay && changed;
    }
}
