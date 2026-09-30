package club.someoneice.humangunner;

/** Geometry used by the live world checks and deterministic boundary tests. */
final class SpawnSafety {
    private record Column(int x, int z, int halfHeight) { }
    private static final Column[] COLUMNS = columns();
    private SpawnSafety() {}

    static boolean farEnough(double dx, double dz) {
        return dx * dx + dz * dz > 96.0D * 96.0D;
    }

    @FunctionalInterface
    interface BlockedCell {
        boolean test(int dx, int dy, int dz);
    }

    @FunctionalInterface
    interface BlockedColumn {
        boolean test(int dx, int dz, int minDy, int maxDy);
    }

    private static Column[] columns() {
        var columns = new java.util.ArrayList<Column>();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                int remaining = 256 - x * x - z * z;
                if (remaining >= 0) columns.add(new Column(x, z, (int) Math.sqrt(remaining)));
            }
        }
        return columns.toArray(Column[]::new);
    }

    static boolean unlitBuffer(BlockedCell blocked) {
        for (Column column : COLUMNS) {
            for (int dy = -column.halfHeight; dy <= column.halfHeight; dy++) {
                if (blocked.test(column.x, dy, column.z)) return false;
            }
        }
        return true;
    }

    static boolean unlitColumns(BlockedColumn blocked) {
        for (Column column : COLUMNS) {
            if (blocked.test(column.x, column.z, -column.halfHeight, column.halfHeight)) return false;
        }
        return true;
    }
}
