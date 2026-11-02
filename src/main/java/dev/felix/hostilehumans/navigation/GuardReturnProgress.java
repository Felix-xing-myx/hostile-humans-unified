package dev.felix.hostilehumans.navigation;

/** Executed route time only: queue waits, combat and unloaded gaps are not failed movement. */
public final class GuardReturnProgress {
    public static final int STALL_TICKS = 60;
    public static final int ATTEMPT_TICKS = 200;
    public static final int FAILURES_PER_BATCH = 4;
    private long lastTick = -1;
    private int elapsed, stationary, bestNode = -1;
    private double bestDistance = Double.POSITIVE_INFINITY;

    public void start() {
        lastTick = -1; elapsed = stationary = 0; bestNode = -1;
        bestDistance = Double.POSITIVE_INFINITY;
    }
    public void pause() { lastTick = -1; }
    public boolean stalled(long now, int node, double distanceSquared) {
        if (lastTick >= 0 && now == lastTick + 1) { elapsed++; stationary++; }
        if (node > bestNode || node == bestNode && distanceSquared + .0625 < bestDistance) {
            bestNode = node; bestDistance = distanceSquared; stationary = 0;
        }
        lastTick = now;
        return stationary >= STALL_TICKS || elapsed >= ATTEMPT_TICKS;
    }
    public static boolean backoff(int failures) { return failures >= FAILURES_PER_BATCH; }
}
