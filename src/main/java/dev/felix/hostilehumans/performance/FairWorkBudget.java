package dev.felix.hostilehumans.performance;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tick-local grants with FIFO rotation. Waiting is not failure and consumes no work. */
public final class FairWorkBudget<K> {
    private record Request(long tick, int maximum, boolean full, String group) {}
    private final int perTick;
    private final int perRequest;
    private final int staleTicks;
    private final LinkedHashMap<K, Request> waiting = new LinkedHashMap<>();
    private final Map<K, Integer> grants = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int remaining, activeCapacity, executed;
    private String lastGroup;
    private int nextTickCapacity;

    public FairWorkBudget(int perTick, int perRequest, int staleTicks) {
        if (perTick <= 0 || perRequest <= 0 || staleTicks < 1) throw new IllegalArgumentException("invalid budget");
        this.perTick = perTick;
        this.nextTickCapacity = perTick;
        this.perRequest = perRequest;
        this.staleTicks = staleTicks;
    }

    /** Sets NEXT tick's throughput only; preserves FIFO, reservations and already consumed work. */
    public void setTickCapacity(int capacity) {
        if (capacity < 1 || capacity > perTick) throw new IllegalArgumentException("capacity exceeds hard limit");
        nextTickCapacity = capacity;
    }

    public int claim(long currentTick, K key, int maximum) {
        return claim(currentTick, key, maximum, false, "");
    }
    public int claim(long currentTick, K key, int maximum, String group) {
        return claim(currentTick, key, maximum, false, group);
    }
    /** A multi-search operation cannot consume a partial grant and starve at the queue tail. */
    public int claimFull(long currentTick, K key, int maximum) {
        return claimFull(currentTick, key, maximum, "");
    }

    public int claimFull(long currentTick, K key, int maximum, String group) {
        if (maximum > perRequest || maximum > perTick) throw new IllegalArgumentException("batch exceeds budget limit");
        return claim(currentTick, key, maximum, true, group);
    }
    private int claim(long currentTick, K key, int maximum, boolean full, String group) {
        if (maximum < 0) throw new IllegalArgumentException("negative request");
        beginTick(currentTick);
        if (maximum == 0) return 0;
        Integer reserved = grants.get(key);
        if (reserved != null) {
            if (reserved == 0) return 0;
            grants.put(key, 0);
            // Rotate behind callers that are still waiting. Completion/stop
            // cancels this reservation; forgotten requests expire promptly.
            waiting.put(key, new Request(currentTick, maximum, full, group));
            int used = full && reserved < maximum ? 0 : Math.min(maximum, reserved);
            remaining += reserved - used;
            executed += used;
            return used;
        }
        waiting.put(key, new Request(currentTick, maximum, full, group));
        // beginTick reserves older requests first. Any remaining capacity is
        // available to new arrivals; served callers in waiting are next-round
        // reservations, not blockers for this tick's spare capacity.
        if (remaining == 0 || full && remaining < maximum) return 0;
        int granted = Math.min(remaining, Math.min(perRequest, maximum));
        remaining -= granted;
        executed += granted;
        grants.put(key, 0);
        // Keep this caller at the tail for the next tick's round.
        waiting.remove(key);
        waiting.put(key, new Request(currentTick, maximum, full, group));
        return granted;
    }

    public void cancel(K key) {
        waiting.remove(key);
        // A positive entry is still reserved. A zero entry marks a claim
        // already settled this tick and must survive a task handoff.
        Integer reserved = grants.get(key);
        if (reserved != null && reserved > 0) {
            grants.remove(key);
            remaining += reserved;
        }
    }

    public String usage(long currentTick, int idleCapacity) {
        if (tick < currentTick - 1 || tick > currentTick) return "0 / " + idleCapacity + " · 等待 " + waiting.size();
        int reserved = activeCapacity - remaining - executed;
        return executed + " / " + activeCapacity + " · 未领用预约 " + reserved
                + " · 待轮转 " + waiting.size();
    }

    private void beginTick(long currentTick) {
        if (tick == currentTick) return;
        if (currentTick < tick) waiting.clear();
        tick = currentTick;
        remaining = nextTickCapacity;
        activeCapacity = remaining;
        executed = 0;
        grants.clear();
        waiting.entrySet().removeIf(entry -> currentTick - entry.getValue().tick() > staleTicks);
        for (var entry : BudgetDemandOrder.interleave(waiting, Request::group, lastGroup)) {
            if (remaining == 0) break;
            int grant = Math.min(remaining, Math.min(perRequest, entry.getValue().maximum()));
            if (entry.getValue().full() && grant < entry.getValue().maximum()) continue;
            remaining -= grant;
            grants.put(entry.getKey(), grant);
            lastGroup = entry.getValue().group();
            waiting.remove(entry.getKey());
        }
    }
}
