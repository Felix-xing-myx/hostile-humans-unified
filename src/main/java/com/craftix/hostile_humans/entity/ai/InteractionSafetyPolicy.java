package com.craftix.hostile_humans.entity.ai;

/** Pure bounds used by passage leases and emergency navigation. */
public final class InteractionSafetyPolicy {
    public static final int CLOSE_DELAY = 20;
    public static final int MAX_CLOSE_WAIT = 100;
    public static final int TNT_RETRY_DELAY = 10;
    public static final int TNT_MAX_RETRIES = 4;
    public static final double TNT_SAFE_DISTANCE_SQUARED = 81.0D;

    private InteractionSafetyPolicy() {}

    public static int firstNearbyNode(int nextIndex) { return Math.max(0, nextIndex - 1); }
    public static int nearbyNodeLimit(int nextIndex, int count) { return Math.min(count, nextIndex + 2); }
    public static boolean nearPassage(double horizontalDistanceSquared) { return horizontalDistanceSquared <= 2.25D; }
    public static boolean escapeTnt(boolean alive, double distanceSquared) {
        return alive && distanceSquared < TNT_SAFE_DISTANCE_SQUARED;
    }
    public static boolean retryTnt(int tick, int nextTick, int retries) {
        return tick >= nextTick && retries < TNT_MAX_RETRIES;
    }
    public static boolean closePassage(long elapsed, boolean occupied, boolean approaching) {
        return elapsed >= CLOSE_DELAY && !occupied && !approaching;
    }
    public static boolean abandonClose(long elapsed) { return elapsed >= MAX_CLOSE_WAIT; }
}
