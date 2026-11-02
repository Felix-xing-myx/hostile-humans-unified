package dev.felix.hostilehumans.settlement;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Root identity comes from the actual StructureStart, never the currently generated chunk or
 * template name alone.
 */
public final class SettlementPlacementIdentity {
    private SettlementPlacementIdentity() {}

    public static UUID root(String dimension, String structure, long startChunk) {
        return UUID.nameUUIDFromBytes(
                ("hhu:settlement:" + dimension + ":" + structure + ":" + startChunk)
                        .getBytes(StandardCharsets.UTF_8));
    }

    public static String module(String template, String module, TemplateTransform transform) {
        return UUID.nameUUIDFromBytes(
                        (template + "/" + module + "/" + transform)
                                .getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
