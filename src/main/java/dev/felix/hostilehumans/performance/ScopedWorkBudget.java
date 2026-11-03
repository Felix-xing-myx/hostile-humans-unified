package dev.felix.hostilehumans.performance;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One FIFO scheduler atomically reserves global and scope capacity for each batch. */
public final class ScopedWorkBudget<K, S> {
    private record Request<S>(long tick, S scope, int amount, String group) {}
    private record Grant<S>(S scope, int amount) {}
    private final int totalPerTick, perScopePerTick, perRequest, staleTicks;
    private final LinkedHashMap<K, Request<S>> waiting = new LinkedHashMap<>();
    private final Map<K, Grant<S>> grants = new HashMap<>();
    private final Map<S, Integer> scopeUsed = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int remaining;
    private String lastGroup;

    public ScopedWorkBudget(int totalPerTick, int perScopePerTick, int perRequest, int staleTicks) {
        if (totalPerTick < 1 || perScopePerTick < 1 || perRequest < 1 || staleTicks < 1)
            throw new IllegalArgumentException("invalid budget");
        this.totalPerTick = totalPerTick;
        this.perScopePerTick = perScopePerTick;
        this.perRequest = perRequest;
        this.staleTicks = staleTicks;
    }

    /** At most one full batch per key per tick; deferred callers keep FIFO priority. */
    public int claimFull(long currentTick, K key, S scope, int amount) {
        return claimFull(currentTick, key, scope, amount, "");
    }

    public int claimFull(long currentTick, K key, S scope, int amount, String group) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(scope, "scope");
        if (amount < 0 || amount > perRequest || amount > totalPerTick || amount > perScopePerTick)
            throw new IllegalArgumentException("batch exceeds budget limit");
        beginTick(currentTick);
        if (amount == 0) return 0;
        Grant<S> grant = grants.get(key);
        if (grant != null) {
            if (grant.amount() == 0) return 0;
            grants.put(key, new Grant<>(scope, 0));
            waiting.put(key, new Request<>(currentTick, scope, amount, group));
            int used = Objects.equals(grant.scope(), scope) && grant.amount() >= amount ? amount : 0;
            release(grant.scope(), grant.amount() - used);
            return used;
        }
        waiting.put(key, new Request<>(currentTick, scope, amount, group));
        if (!fits(scope, amount)) return 0;
        reserve(scope, amount);
        lastGroup = group;
        grants.put(key, new Grant<>(scope, 0));
        waiting.remove(key);
        waiting.put(key, new Request<>(currentTick, scope, amount, group));
        return amount;
    }

    /** Drops deferred work and returns only capacity that has not been claimed. */
    public void cancel(K key) {
        waiting.remove(key);
        grants.computeIfPresent(key, (ignored, grant) -> {
            release(grant.scope(), grant.amount());
            return new Grant<>(grant.scope(), 0);
        });
    }

    private void release(S scope, int amount) {
        if (amount == 0) return;
        remaining += amount;
        scopeUsed.computeIfPresent(scope, (ignored, used) -> used == amount ? null : used - amount);
    }

    private boolean fits(S scope, int amount) {
        return remaining >= amount && scopeUsed.getOrDefault(scope, 0) + amount <= perScopePerTick;
    }
    private void reserve(S scope, int amount) {
        remaining -= amount;
        scopeUsed.merge(scope, amount, Integer::sum);
    }
    private void beginTick(long currentTick) {
        if (tick == currentTick) return;
        if (currentTick < tick) {
            waiting.clear();
            lastGroup = null;
        }
        tick = currentTick;
        remaining = totalPerTick;
        scopeUsed.clear();
        // New callers can arrive after a served caller requeues within the same
        // tick. Rotate actual unserved work ahead of those next-round requests.
        LinkedHashMap<K, Request<S>> rotated = new LinkedHashMap<>();
        waiting.forEach((key, request) -> { if (!grants.containsKey(key)) rotated.put(key, request); });
        waiting.forEach((key, request) -> { if (grants.containsKey(key)) rotated.put(key, request); });
        waiting.clear();
        waiting.putAll(rotated);
        grants.clear();
        waiting.entrySet().removeIf(e -> currentTick - e.getValue().tick() > staleTicks);
        var demand = BudgetDemandOrder.interleave(waiting, Request::group, lastGroup);
        while (remaining > 0 && demand.hasNext()) {
            var entry = demand.next();
            Request<S> request = entry.getValue();
            // A saturated dimension must not block eligible dimensions behind it.
            if (!fits(request.scope(), request.amount())) continue;
            reserve(request.scope(), request.amount());
            grants.put(entry.getKey(), new Grant<>(request.scope(), request.amount()));
            lastGroup = entry.getValue().group();
            waiting.remove(entry.getKey());
        }
    }
}
