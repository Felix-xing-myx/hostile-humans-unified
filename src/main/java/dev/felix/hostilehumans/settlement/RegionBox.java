package dev.felix.hostilehumans.settlement;

/**
 * Inclusive block-cell box; position queries use [minimum, maximum+1), never rounded entity
 * coordinates.
 */
public record RegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public RegionBox {
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("inverted region box");
    }

    public static RegionBox between(TemplateTransform.Cell a, TemplateTransform.Cell b) {
        return new RegionBox(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()),
                Math.max(a.z(), b.z()));
    }

    public boolean contains(double x, double y, double z) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && x >= minX
                && x < (double) maxX + 1
                && y >= minY
                && y < (double) maxY + 1
                && z >= minZ
                && z < (double) maxZ + 1;
    }

    public RegionBox transform(TemplateTransform transform) {
        // Horizontal orthogonal transforms preserve axis-aligned block boxes, including their
        // inclusive cell endpoints.
        return between(
                transform.apply(new TemplateTransform.Cell(minX, minY, minZ)),
                transform.apply(new TemplateTransform.Cell(maxX, maxY, maxZ)));
    }

    public RegionBox union(RegionBox other) {
        return new RegionBox(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ));
    }

    public long sizeX() {
        return (long) maxX - minX + 1;
    }

    public long sizeY() {
        return (long) maxY - minY + 1;
    }

    public long sizeZ() {
        return (long) maxZ - minZ + 1;
    }
}
