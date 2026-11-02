package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.faction.FactionDefinitions;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;

/**
 * Immutable validated local author payload. Unknown versions are retained by the owning BE/codec,
 * never guessed.
 */
public record SettlementTemplate(
        String template,
        String module,
        Kind kind,
        String faction,
        int tier,
        int revision,
        TemplateTransform.Cell size,
        TemplateTransform.Cell originOffset,
        List<SettlementRegion> regions,
        List<SettlementFacility> facilities) {
    public enum Kind {
        ROOT,
        ATTACHMENT,
        ORDINARY
    }

    public static final int SCHEMA = 1;

    public SettlementTemplate {
        if (ResourceLocation.tryParse(template) == null
                || module == null
                || !module.matches("[a-z0-9_./-]{1,64}")
                || kind == null
                || faction == null
                || FactionDefinitions.find(faction) == null
                || tier < 1
                || tier > 3
                || revision < 0
                || size == null
                || size.x() < 1
                || size.y() < 1
                || size.z() < 1
                || size.x() > 512
                || size.y() > 512
                || size.z() > 512
                || originOffset == null)
            throw new IllegalArgumentException("invalid template identity, faction or size");
        faction = FactionDefinitions.find(faction).id().toString();
        regions = List.copyOf(regions);
        facilities = List.copyOf(facilities);
        if (regions.size() > SettlementLimits.value(SettlementLimits.REGIONS)
                || facilities.size() > 128)
            throw new IllegalArgumentException("template list limit");
        var ids = new HashSet<String>();
        int boxes = 0;
        RegionBox envelope = null;
        for (var region : regions) {
            if (!ids.add(region.id()))
                throw new IllegalArgumentException("duplicate region " + region.id());
            if (region.includes().size() + region.excludes().size()
                    > SettlementLimits.value(SettlementLimits.REGION_BOXES))
                throw new IllegalArgumentException("region box limit");
            for (var box :
                    java.util.stream.Stream.concat(
                                    region.includes().stream(), region.excludes().stream())
                            .toList()) {
                boxes++;
                int extension = SettlementLimits.value(SettlementLimits.EXTENSION);
                if (box.minX() < -(long) extension
                        || box.minY() < -(long) extension
                        || box.minZ() < -(long) extension
                        || box.maxX() >= (long) size.x() + extension
                        || box.maxY() >= (long) size.y() + extension
                        || box.maxZ() >= (long) size.z() + extension)
                    throw new IllegalArgumentException(
                            "box outside allowed extension: " + region.id());
                envelope = envelope == null ? box : envelope.union(box);
            }
        }
        int extent = SettlementLimits.value(SettlementLimits.EXTENT);
        if (boxes > SettlementLimits.value(SettlementLimits.TEMPLATE_BOXES)
                || envelope != null
                        && (envelope.sizeX() > extent
                                || envelope.sizeY() > extent
                                || envelope.sizeZ() > extent))
            throw new IllegalArgumentException("template total box/extent limit");
        if (kind == Kind.ROOT
                && regions.stream().noneMatch(r -> r.layer() == SettlementRegion.Layer.OUTER))
            throw new IllegalArgumentException("protected root needs outer region");
        if (tier == 1 && regions.stream().anyMatch(r -> r.layer() == SettlementRegion.Layer.INNER))
            throw new IllegalArgumentException("tier one has no inner region");
        var facilityIds = new HashSet<String>();
        int controls = 0;
        for (var facility : facilities) {
            if (!facilityIds.add(facility.id()))
                throw new IllegalArgumentException("duplicate facility " + facility.id());
            var region =
                    regions.stream()
                            .filter(r -> r.id().equals(facility.region()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalArgumentException("missing facility region"));
            var p = facility.position();
            if (!region.contains(p.x() + .5, p.y(), p.z() + .5))
                throw new IllegalArgumentException("facility anchor outside referenced region");
            if (facility.type() == SettlementFacility.Type.CONTROL) controls++;
            if (facility.type() == SettlementFacility.Type.TRAINING
                    && (tier < 2 || region.layer() != SettlementRegion.Layer.INNER))
                throw new IllegalArgumentException("training needs tier two inner region");
            if ((facility.type() == SettlementFacility.Type.SUPPLY
                            || facility.type() == SettlementFacility.Type.TRADER)
                    && region.layer() != SettlementRegion.Layer.OUTER)
                throw new IllegalArgumentException("basic supply/trader belongs to outer region");
        }
        if (controls > 1) throw new IllegalArgumentException("duplicate template control");
        int ix = -originOffset.x(), iy = -originOffset.y(), iz = -originOffset.z();
        if (ix < 0 || iy < 0 || iz < 0 || ix >= size.x() || iy >= size.y() || iz >= size.z())
            throw new IllegalArgumentException(
                    "information block must lie inside template export size");
    }

    public int boxCount() {
        return regions.stream().mapToInt(r -> r.includes().size() + r.excludes().size()).sum();
    }

    public SettlementTemplate revision(int next) {
        return new SettlementTemplate(
                template,
                module,
                kind,
                faction,
                tier,
                next,
                size,
                originOffset,
                regions,
                facilities);
    }
}
