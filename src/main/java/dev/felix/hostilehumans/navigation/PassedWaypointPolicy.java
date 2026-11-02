package dev.felix.hostilehumans.navigation;

/** Recognize a short forward crossing, not a nearest-node shortcut or arbitrary path skip. */
public final class PassedWaypointPolicy {
    private PassedWaypointPolicy() {}

    public static boolean crossed(double oldX, double oldZ, double x, double z,
            double nodeX, double nodeZ, double nextX, double nextZ, double corridor) {
        if (!Double.isFinite(oldX) || !Double.isFinite(oldZ) || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(nodeX) || !Double.isFinite(nodeZ) || !Double.isFinite(nextX)
                || !Double.isFinite(nextZ) || !Double.isFinite(corridor)) return false;
        if (corridor <= 0 || corridor > .75) return false;
        double dx = nextX - nodeX, dz = nextZ - nodeZ;
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < .25 || lengthSqr > 2.01) return false;
        double moveX = x - oldX, moveZ = z - oldZ, movedSqr = moveX * moveX + moveZ * moveZ;
        double forward = moveX * dx + moveZ * dz;
        if (movedSqr < 1.0E-6 || movedSqr > 16 || forward <= 0
                || forward * forward < .81 * movedSqr * lengthSqr) return false;
        double before = (oldX - nodeX) * dx + (oldZ - nodeZ) * dz;
        double after = (x - nodeX) * dx + (z - nodeZ) * dz;
        if (before > .05 || after < .05) return false;
        double fraction = Math.max(0, Math.min(1, -before / (after - before)));
        double crossingX = oldX + moveX * fraction - nodeX;
        double crossingZ = oldZ + moveZ * fraction - nodeZ;
        double cross = crossingX * dz - crossingZ * dx;
        double lateral = (x - nodeX) * dz - (z - nodeZ) * dx;
        return cross * cross <= corridor * corridor * lengthSqr
                && lateral * lateral <= corridor * corridor * lengthSqr;
    }
}
