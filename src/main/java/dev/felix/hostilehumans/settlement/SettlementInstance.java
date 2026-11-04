package dev.felix.hostilehumans.settlement;

import java.util.List;
import java.util.UUID;

/**
 * Dimension-local instance snapshot; no mutable entity, player list, inventory or derived
 * permissions.
 */
public record SettlementInstance(
        UUID id,
        String name,
        String faction,
        int tier,
        int revision,
        List<SettlementModule> modules,
        boolean incomplete) {
    public SettlementInstance {
        if (id == null
                || name == null
                || name.isBlank()
                || name.length() > 96
                || faction == null
                || tier < 1
                || tier > 3
                || revision < 1
                || modules == null
                || modules.isEmpty()) throw new IllegalArgumentException("instance identity");
        modules = List.copyOf(modules);
        if (modules.size() > SettlementLimits.value(SettlementLimits.MODULES)
                || modules.stream().mapToInt(m -> m.author().boxCount()).sum()
                        > SettlementLimits.value(SettlementLimits.INSTANCE_BOXES))
            throw new IllegalArgumentException("instance input limit");
        var keys = new java.util.HashSet<String>();
        int controls = 0, roots = 0;
        for (var m : modules) {
            if (!keys.add(m.key()) || !m.author().faction().equals(faction))
                throw new IllegalArgumentException("module identity/faction conflict");
            if (m.author().kind().root()) {
                if (m.author().tier() != tier)
                    throw new IllegalArgumentException("root/instance tier conflict");
                roots++;
            }
            if (tier == 1
                    && m.regions().stream()
                            .anyMatch(region -> region.layer() == SettlementRegion.Layer.INNER))
                throw new IllegalArgumentException(
                        "tier-one instance cannot acquire an inner annex");
            controls +=
                    m.author().facilities().stream()
                            .filter(f -> f.type() == SettlementFacility.Type.CONTROL)
                            .count();
        }
        if (roots != 1)
            throw new IllegalArgumentException("instance requires exactly one root module");
        if (controls > 1)
            throw new IllegalArgumentException("instance has conflicting control facilities");
    }

    public List<String> brokerFactions() {
        return modules.stream().filter(module -> module.author().kind().root())
                .findFirst().orElseThrow().author().brokerFactions();
    }

    public SettlementInstance rename(String next) {
        return new SettlementInstance(
                id, next, faction, tier, Math.addExact(revision, 1), modules, incomplete);
    }
}
