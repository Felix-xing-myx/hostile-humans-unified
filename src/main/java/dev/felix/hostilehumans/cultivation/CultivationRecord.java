package dev.felix.hostilehumans.cultivation;

import java.util.UUID;

/**
 * Saved training journal. Entity allocation is applied from a committed revision, never
 * reconstructed from nearby mobs.
 */
final class CultivationRecord {
    final UUID soldier, owner;
    CultivationFacility facility;
    CultivationProfile profile;
    CultivationProfile previous;
    CultivationCycle cycle;
    int seat = -1;
    long appliedRevision = -1;
    String name, reason = "", command = "";

    CultivationRecord(
            UUID soldier,
            UUID owner,
            CultivationFacility facility,
            CultivationProfile profile,
            String name) {
        this.soldier = soldier;
        this.owner = owner;
        this.facility = facility;
        this.profile = profile;
        this.previous = profile;
        this.name = name;
    }

    boolean occupies() {
        return seat >= 0
                && cycle != null
                && (cycle.status() == CultivationCycle.Status.MOVING
                        || cycle.status() == CultivationCycle.Status.ACTIVE
                        || cycle.status() == CultivationCycle.Status.READY);
    }

    boolean pending() {
        return cycle != null && cycle.status() == CultivationCycle.Status.COMPLETED;
    }
}
