package club.someoneice.humangunner;

/** Constant-velocity intercept, using blocks/tick and blocks/tick squared throughout. */
final class GunTrackingPolicy {
    static final double MAX_LEAD_TICKS = 20;
    private GunTrackingPolicy() {}
    static double flightTicks(double x, double y, double z, double vx, double vy, double vz,
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
