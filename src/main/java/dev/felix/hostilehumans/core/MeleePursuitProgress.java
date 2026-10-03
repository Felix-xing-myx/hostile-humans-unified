package dev.felix.hostilehumans.core;

/** Tracks actual combat movement, not path identity or repeatedly recreated waypoints. */
public final class MeleePursuitProgress {
    private int targetId = Integer.MIN_VALUE;
    private int progressTick;
    private double x, y, z;

    public boolean stalled(int tick, int target, double currentX, double currentY,
            double currentZ, boolean needsPursuit) {
        double dx = currentX - x, dy = currentY - y, dz = currentZ - z;
        if (!needsPursuit || target != targetId || tick < progressTick
                || dx * dx + dy * dy + dz * dz >= 0.09D) {
            targetId = target;
            progressTick = tick;
            x = currentX; y = currentY; z = currentZ;
            return false;
        }
        if (tick - progressTick < 30) return false;
        // Report at most once per 30 ticks, even if every replacement path fails.
        progressTick = tick;
        return true;
    }
}
