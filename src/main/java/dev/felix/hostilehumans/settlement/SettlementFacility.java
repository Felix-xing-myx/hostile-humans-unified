package dev.felix.hostilehumans.settlement;

/** A stable module-local facility reference. Runtime inventory/occupants are not author data. */
public record SettlementFacility(
        String id, Type type, String region, TemplateTransform.Cell position) {
    public enum Type {
        CONTROL,
        ENTRANCE,
        SUPPLY,
        TRADER,
        POST,
        TRAINING
    }

    public SettlementFacility {
        if (id == null
                || !id.matches("[a-z0-9_./-]{1,64}")
                || type == null
                || region == null
                || position == null)
            throw new IllegalArgumentException("invalid facility reference");
    }

    public SettlementFacility transform(TemplateTransform transform) {
        return new SettlementFacility(id, type, region, transform.apply(position));
    }
}
