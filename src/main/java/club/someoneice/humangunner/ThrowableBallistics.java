package club.someoneice.humangunner;

/** Fixed launch speed under native move -> drag -> gravity ordering.
 * Fractional flight times interpolate the final tick's straight movement segment.
 * No world access, optional API, entity creation or per-tick state. */
final class ThrowableBallistics {
    static final double DRAG = (double) .99F;
    record Launch(double x, double y, double z, double flightTicks) { }

    private ThrowableBallistics() { }

    static Launch solve(double dx, double dy, double dz, double gravity, double speed, int horizon) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || !Double.isFinite(gravity) || gravity < 0 || !Double.isFinite(speed) || speed <= 0
                || horizon < 2 || dx * dx + dy * dy + dz * dz < 1.0E-12) return null;
        double squaredSpeed = speed * speed;
        for (int tick = 1; tick < horizon; tick++) {
            if (speedSquared(dx, dy, dz, gravity, tick) > squaredSpeed) continue;
            // The first reachable tick brackets the fast, low-arc solution.
            // Selecting only its integer endpoint silently reduced actual launch speed.
            double low = tick - 1, high = tick;
            for (int iteration = 0; iteration < 36; iteration++) {
                double mid = (low + high) * .5;
                if (speedSquared(dx, dy, dz, gravity, mid) > squaredSpeed) low = mid;
                else high = mid;
            }
            double travel = travel(high);
            return new Launch(dx / travel, (dy + drop(gravity, high)) / travel, dz / travel, high);
        }
        return null;
    }

    private static double speedSquared(double dx, double dy, double dz, double gravity, double ticks) {
        double travel = travel(ticks), rise = dy + drop(gravity, ticks);
        return (dx * dx + rise * rise + dz * dz) / (travel * travel);
    }

    private static double travel(double ticks) {
        int whole = (int) ticks;
        double drag = Math.pow(DRAG, whole);
        return (1 - drag) / (1 - DRAG) + (ticks - whole) * drag;
    }

    private static double drop(double gravity, double ticks) {
        int whole = (int) ticks;
        double sum = (1 - Math.pow(DRAG, whole)) / (1 - DRAG);
        return gravity / (1 - DRAG) * (whole - sum + (ticks - whole) * (1 - Math.pow(DRAG, whole)));
    }
}
