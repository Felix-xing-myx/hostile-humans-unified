package dev.felix.hostilehumans.behavior.combat.gun;

/** Constant-velocity intercept, using blocks/tick and blocks/tick squared throughout. */
public final class GunTrackingPolicy {
    static final double MAX_LEAD_TICKS = 20;
    private GunTrackingPolicy() {}
    /** Distance multiplier after fractional move-then-drag ticks. */
    public static double stepTravel(double ticks, double drag) {
        double t = Math.max(0, Math.min(MAX_LEAD_TICKS, ticks));
        int n = (int) t;
        double power = Math.pow(drag, n);
        double whole = Math.abs(1 - drag) < 1.0E-8 ? n : (1 - power) / (1 - drag);
        return whole + (t - n) * power;
    }

    /** Positive drop: gravity is applied after movement, so the first tick has no drop. */
    public static double stepDrop(double ticks, double drag, double gravity) {
        double t = Math.max(0, Math.min(MAX_LEAD_TICKS, ticks));
        int n = (int) t;
        double fraction = t - n;
        if (Math.abs(1 - drag) < 1.0E-8)
            return gravity * (n * (n - 1) * .5 + fraction * n);
        double sum = (1 - Math.pow(drag, n)) / (1 - drag);
        return gravity * ((n - sum) / (1 - drag) + fraction * sum);
    }

    /** Bounded earliest intercept for a backend which explicitly supplies discrete tick physics. */
    public static double stepFlightTicks(double x, double y, double z, double vx, double vy, double vz,
            double speed, double gravity, double drag) {
        if (!(speed > 0) || !Double.isFinite(x + y + z + vx + vy + vz + speed + gravity + drag)
                || drag < 0 || drag > 1) return 0;
        if (x * x + y * y + z * z < 1.0E-12) return 0;
        double low = 0;
        for (int end = 1; end <= MAX_LEAD_TICKS; end++) {
            if (stepGap(end, x, y, z, vx, vy, vz, speed, gravity, drag) <= 0) {
                double high = end;
                for (int i = 0; i < 16; i++) {
                    double mid = (low + high) * .5;
                    if (stepGap(mid, x, y, z, vx, vy, vz, speed, gravity, drag) > 0) low = mid;
                    else high = mid;
                }
                return (low + high) * .5;
            }
            low = end;
        }
        return MAX_LEAD_TICKS;
    }

    private static double stepGap(double t, double x, double y, double z, double vx, double vy,
            double vz, double speed, double gravity, double drag) {
        double dx = x + vx * t, dy = y + vy * t + stepDrop(t, drag, gravity), dz = z + vz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - speed * stepTravel(t, drag);
    }

    public static double flightTicks(double x, double y, double z, double vx, double vy, double vz,
                              double speed, double gravity) {
        if (!(speed > 0) || !Double.isFinite(speed)) return 0;
        double a = vx * vx + vy * vy + vz * vz - speed * speed;
        double b = 2 * (x * vx + y * vy + z * vz);
        double c = x * x + y * y + z * z;
        if (!Double.isFinite(a + b + c + gravity)) return 0;
        double t = Math.sqrt(c) / speed;
        if (Math.abs(a) < 1.0E-8) {
            if (b < -1.0E-8) t = -c / b;
        } else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant >= 0) {
                double root = Math.sqrt(discriminant);
                double first = (-b - root) / (2 * a), second = (-b + root) / (2 * a);
                if (first > 0 && second > 0) t = Math.min(first, second);
                else if (first > 0 || second > 0) t = Math.max(first, second);
            }
        }
        t = Math.min(MAX_LEAD_TICKS, Math.max(0, t));
        // Refine the intercept for the actual weapon's gravitational drop.
        if (gravity != 0) for (int i = 0; i < 4; i++) {
            double dx = x + vx * t, dy = y + vy * t + .5 * gravity * t * t, dz = z + vz * t;
            t = Math.min(MAX_LEAD_TICKS, Math.sqrt(dx * dx + dy * dy + dz * dz) / speed);
        }
        return t;
    }
}
