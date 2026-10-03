package dev.felix.hostilehumans.core;

/** Five-tick progress windows scale with expected speed, not a fixed block threshold. */
public final class MovementContinuityProgress {
    private int since;
    private double x, z;
    private boolean active;

    public boolean stalled(int tick, double currentX, double currentZ, double speed, boolean moving) {
        if (!moving || !active || tick < since) {
            active = moving;
            since = tick;
            x = currentX;
            z = currentZ;
            return false;
        }
        if (tick - since < 5) return false;
        double dx = currentX - x, dz = currentZ - z;
        double minimum = Math.max(0.005D, Math.min(0.08D, Math.max(0.0D, speed) * 0.5D));
        boolean stuck = dx * dx + dz * dz < minimum * minimum;
        since = tick;
        x = currentX;
        z = currentZ;
        return stuck;
    }
}
