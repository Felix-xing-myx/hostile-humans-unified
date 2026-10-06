package club.someoneice.humangunner;

/** Idle-shore watchdog: bobbing and circling cannot masquerade as route progress. */
public final class ShoreProgressTracker {
    public static final int STALL_TICKS = 60;
    private static final double MINIMUM_APPROACH = 0.25D;
    private int lastProgressTick;
    private int node;
    private double bestDistance;

    public void reset(int tick) {
        lastProgressTick = tick;
        node = -1;
        bestDistance = Double.POSITIVE_INFINITY;
    }

    public void observe(int tick, int pathNode, double horizontalDistance) {
        if (!Double.isFinite(horizontalDistance)) return;
        if (pathNode > node) {
            node = pathNode;
            bestDistance = horizontalDistance;
            lastProgressTick = tick;
        } else if (horizontalDistance <= bestDistance - MINIMUM_APPROACH) {
            bestDistance = horizontalDistance;
            lastProgressTick = tick;
        }
    }

    public boolean isStalled(int tick) {
        return tick - lastProgressTick >= STALL_TICKS;
    }
}
