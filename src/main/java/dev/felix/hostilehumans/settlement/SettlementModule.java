package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

/**
 * Immutable author placement with world geometry baked once, not transformed again for every
 * permission query.
 */
public final class SettlementModule {
    private final String key;
    private final SettlementTemplate author;
    private final TemplateTransform transform;
    private final BlockPos informationBlock;
    private final List<SettlementRegion> regions;
    private final List<SettlementFacility> facilities;

    public SettlementModule(
            String key,
            SettlementTemplate author,
            TemplateTransform transform,
            BlockPos informationBlock) {
        if (key == null
                || key.length() > 160
                || author == null
                || transform == null
                || informationBlock == null) throw new IllegalArgumentException("module placement");
        author.validateForPublication();
        this.key = key;
        this.author = author;
        this.transform = transform;
        this.informationBlock = informationBlock.immutable();
        var offset = author.originOffset();
        var carrier =
                transform.apply(
                        new TemplateTransform.Cell(
                                Math.negateExact(offset.x()),
                                Math.negateExact(offset.y()),
                                Math.negateExact(offset.z())));
        if (!carrier.equals(
                new TemplateTransform.Cell(
                        informationBlock.getX(), informationBlock.getY(), informationBlock.getZ())))
            throw new IllegalArgumentException("module information/origin mismatch");
        regions = author.regions().stream().map(region -> region.transform(transform)).toList();
        facilities =
                author.facilities().stream()
                        .map(facility -> facility.transform(transform))
                        .toList();
    }

    public String key() {
        return key;
    }

    public SettlementTemplate author() {
        return author;
    }

    public TemplateTransform transform() {
        return transform;
    }

    public BlockPos informationBlock() {
        return informationBlock;
    }

    public List<SettlementRegion> regions() {
        return regions;
    }

    public List<SettlementFacility> facilities() {
        return facilities;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof SettlementModule other
                && key.equals(other.key)
                && author.equals(other.author)
                && transform.equals(other.transform)
                && informationBlock.equals(other.informationBlock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, author, transform, informationBlock);
    }
}
