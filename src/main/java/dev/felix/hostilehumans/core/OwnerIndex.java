package dev.felix.hostilehumans.core;

import java.util.*;

/** Thread-confined bidirectional ownership index; callers cannot mutate snapshots. */
public final class OwnerIndex {
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, Set<UUID>> members = new HashMap<>();
    public UUID assign(UUID entity, UUID owner) {
        Objects.requireNonNull(entity, "entity");
        UUID previous = owners.remove(entity);
        if (previous != null) {
            Set<UUID> group = members.get(previous);
            group.remove(entity);
            if (group.isEmpty()) members.remove(previous);
        }
        if (owner != null) {
            owners.put(entity, owner);
            members.computeIfAbsent(owner, key -> new LinkedHashSet<>()).add(entity);
        }
        return previous;
    }
    public UUID owner(UUID entity) { return owners.get(entity); }
    public Set<UUID> members(UUID owner) {
        Set<UUID> ids = members.get(owner);
        return ids == null ? Set.of() : Set.copyOf(ids);
    }
}
