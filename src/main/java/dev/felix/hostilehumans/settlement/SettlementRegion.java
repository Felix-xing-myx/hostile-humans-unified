package dev.felix.hostilehumans.settlement;

import java.util.List;

/**
 * Geometry only: identity, union of includes minus local exclusions. Never controls AI or grants
 * permission.
 */
public record SettlementRegion(
        String id, String name, Layer layer, List<RegionBox> includes, List<RegionBox> excludes) {
    public enum Layer {
        OUTER,
        INNER
    }

    public SettlementRegion {
        if (id == null
                || !id.matches("[a-z0-9_./-]{1,64}")
                || name == null
                || name.length() > 96
                || layer == null) throw new IllegalArgumentException("region identity");
        includes = List.copyOf(includes);
        excludes = List.copyOf(excludes);
        if (includes.isEmpty() || includes.size() + excludes.size() > 64)
            throw new IllegalArgumentException("region boxes");
    }

    public boolean contains(double x, double y, double z) {
        return includes.stream().anyMatch(b -> b.contains(x, y, z))
                && excludes.stream().noneMatch(b -> b.contains(x, y, z));
    }

    public RegionBox envelope() {
        RegionBox result = includes.get(0);
        for (int i = 1; i < includes.size(); i++) result = result.union(includes.get(i));
        return result;
    }

    public SettlementRegion transform(TemplateTransform transform) {
        return new SettlementRegion(
                id,
                name,
                layer,
                includes.stream().map(b -> b.transform(transform)).toList(),
                excludes.stream().map(b -> b.transform(transform)).toList());
    }
}
