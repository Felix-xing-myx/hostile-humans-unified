package club.someoneice.humangunner;

/** Bounded whole-route geometry; a short approach around a wall is permitted. */
public final class RetreatPathSafetyPolicy {
    public static final int MAX_NODES = 192;
    private RetreatPathSafetyPolicy() {}

    public static boolean isSafe(double originX, double originZ, double threatX, double threatZ,
            double[][] nodes, boolean requireGain) {
        if (nodes.length == 0 || nodes.length > MAX_NODES) return false;
        double initial = Math.hypot(originX - threatX, originZ - threatZ);
        double clearance = Math.min(3.0D, initial);
        double minimum = Math.max(clearance, initial - Math.min(3.0D, initial * 0.25D));
        double length = 0.0D, approachLength = 0.0D;
        double x = originX, z = originZ;
        for (double[] node : nodes) {
            if (node.length < 2 || !Double.isFinite(node[0]) || !Double.isFinite(node[1])) return false;
            double dx = node[0] - x, dz = node[1] - z;
            double segment = Math.hypot(dx, dz);
            double segmentSqr = dx * dx + dz * dz;
            double fraction = segmentSqr == 0 ? 0 : Math.max(0, Math.min(1,
                    ((threatX - x) * dx + (threatZ - z) * dz) / segmentSqr));
            double closest = Math.hypot(x + fraction * dx - threatX, z + fraction * dz - threatZ);
            if (closest + 1.0E-6D < minimum) return false;
            double nextDistance = Math.hypot(node[0] - threatX, node[1] - threatZ);
            // Only the first eight travelled blocks may dip inside the start radius.
            if (nextDistance < initial && nextDistance < Math.hypot(x - threatX, z - threatZ)) {
                approachLength += segment;
                if (length + segment > 8.0D || approachLength > 6.0D) return false;
            }
            length += segment;
            x = node[0]; z = node[1];
        }
        double endDistance = Math.hypot(x - threatX, z - threatZ);
        double direct = Math.hypot(x - originX, z - originZ);
        // Bounded U-shaped detours, not arbitrary loops or a long tour near the enemy.
        return length <= Math.min(128.0D, direct * 2.0D + 12.0D)
                && (requireGain ? endDistance >= initial + 3.0D : endDistance >= initial - 1.0E-6D);
    }
}
