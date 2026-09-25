package club.someoneice.humangunner;

/** Geometry used by the live world checks and deterministic boundary tests. */
final class SpawnSafety {
    private SpawnSafety() {}

    static boolean farEnough(double dx, double dz) {
        return dx * dx + dz * dz > 96.0D * 96.0D;
    }

    @FunctionalInterface
    interface BlockedCell {
        boolean test(int dx, int dy, int dz);
    }

    static boolean unlitBuffer(BlockedCell blocked) {
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int dy = -16; dy <= 16; dy++) {
                    if (dx * dx + dy * dy + dz * dz <= 256 && blocked.test(dx, dy, dz)) return false;
                }
            }
        }
        return true;
    }
}
