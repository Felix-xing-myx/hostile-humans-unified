package dev.felix.hostilehumans.behavior.combat.retreat;

/** A retreat can be blocked despite a nonempty path. Progress means real net displacement, not animation or speed input. */
public final class RetreatEscapeProgress {
    public static final int NO_ROUTE_TICKS = 12, STALLED_ROUTE_TICKS = 20;
    public static final double PROGRESS_DISTANCE = .5;
    private boolean initialized, blocked;
    private long progressAt, observedAt;
    private double x, y, z;

    public boolean update(long tick, double nextX, double nextY, double nextZ,
            boolean routeUnavailable, boolean verticalEscape) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) {
            initialized = blocked = false; return false;
        }
        if (!initialized || tick < observedAt || hasProgress(nextX, nextY, nextZ, verticalEscape)) {
            initialized = true; blocked = false;
            x = nextX; y = nextY; z = nextZ; progressAt = tick;
        }
        observedAt = tick;
        if (tick - progressAt >= (routeUnavailable ? NO_ROUTE_TICKS : STALLED_ROUTE_TICKS)) blocked = true;
        return blocked;
    }
    public boolean hasProgress(double nextX, double nextY, double nextZ, boolean verticalEscape) {
        double dx = nextX - x, dy = nextY - y, dz = nextZ - z;
        return initialized && (dx * dx + dz * dz >= PROGRESS_DISTANCE * PROGRESS_DISTANCE
                || verticalEscape && Math.abs(dy) >= PROGRESS_DISTANCE);
    }
}
