package dev.felix.hostilehumans.cultivation;

import java.util.UUID;

/**
 * A concrete training block is referenced by dimension, stable instance and immutable placement
 * revision.
 */
public record CultivationFacility(
        String dimension, UUID id, int x, int y, int z, long revision, boolean testMode) {
    public CultivationFacility {
        if (dimension == null || id == null || revision < 0)
            throw new IllegalArgumentException("Invalid training facility");
    }
}
