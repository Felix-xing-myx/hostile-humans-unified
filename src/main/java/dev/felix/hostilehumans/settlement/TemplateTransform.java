package dev.felix.hostilehumans.settlement;

/**
 * Mirror, quarter turn around the actual placement pivot, then world translation. Matches vanilla
 * block-cell transforms.
 */
public record TemplateTransform(Cell origin, Cell pivot, Mirror mirror, int clockwiseQuarterTurns) {
    public enum Mirror {
        NONE,
        LEFT_RIGHT,
        FRONT_BACK
    }

    public record Cell(int x, int y, int z) {}

    public TemplateTransform {
        if (origin == null || pivot == null || mirror == null)
            throw new IllegalArgumentException("transform");
        clockwiseQuarterTurns = Math.floorMod(clockwiseQuarterTurns, 4);
    }

    public Cell apply(Cell local) {
        long x = local.x(), z = local.z();
        if (mirror == Mirror.FRONT_BACK) x = -x;
        if (mirror == Mirror.LEFT_RIGHT) z = -z;
        long px = pivot.x(), pz = pivot.z(), rx = x, rz = z;
        switch (clockwiseQuarterTurns) {
            case 1 -> {
                rx = px + pz - z;
                rz = pz - px + x;
            }
            case 2 -> {
                rx = 2 * px - x;
                rz = 2 * pz - z;
            }
            case 3 -> {
                rx = px - pz + z;
                rz = px + pz - x;
            }
            default -> {}
        }
        return new Cell(
                Math.toIntExact(rx + origin.x()),
                Math.toIntExact((long) local.y() + origin.y()),
                Math.toIntExact(rz + origin.z()));
    }

    public Cell unapply(Cell world) {
        long x = (long) world.x() - origin.x(), z = (long) world.z() - origin.z();
        long px = pivot.x(), pz = pivot.z(), rx = x, rz = z;
        switch (clockwiseQuarterTurns) {
            case 1 -> {
                rx = px - pz + z;
                rz = px + pz - x;
            }
            case 2 -> {
                rx = 2 * px - x;
                rz = 2 * pz - z;
            }
            case 3 -> {
                rx = px + pz - z;
                rz = pz - px + x;
            }
            default -> {}
        }
        if (mirror == Mirror.FRONT_BACK) rx = -rx;
        if (mirror == Mirror.LEFT_RIGHT) rz = -rz;
        return new Cell(
                Math.toIntExact(rx),
                Math.toIntExact((long) world.y() - origin.y()),
                Math.toIntExact(rz));
    }
}
