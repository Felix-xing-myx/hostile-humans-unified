package dev.felix.hostilehumans.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tick-local grants with FIFO rotation. Waiting is not failure and consumes no work. */
public final class FairWorkBudget<K> {
    private record Request(long tick, int maximum) {}
    private final int perTick;
    private final int perRequest;
    private final int staleTicks;
    private final LinkedHashMap<K, Request> waiting = new LinkedHashMap<>();
    private final Map<K, Integer> grants = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int remaining;

    public FairWorkBudget(int perTick, int perRequest, int staleTicks) {
        if (perTick <= 0 || perRequest <= 0 || staleTicks < 1) throw new IllegalArgumentException("invalid budget");
        this.perTick = perTick;
        this.perRequest = perRequest;
        this.staleTicks = staleTicks;
    }

    public int claim(long currentTick, K key, int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("negative request");
        beginTick(currentTick);
        if (maximum == 0) return 0;
        Integer reserved = grants.get(key);
        if (reserved != null) {
            if (reserved == 0) return 0;
            grants.put(key, 0);
            // Rotate behind callers that are still waiting. Completion/stop
            // cancels this reservation; forgotten requests expire promptly.
            waiting.put(key, new Request(currentTick, maximum));
            return Math.min(maximum, reserved);
        }
        waiting.put(key, new Request(currentTick, maximum));
        // beginTick reserves older requests first. Any remaining capacity is
        // available to new arrivals; served callers in waiting are next-round
        // reservations, not blockers for this tick's spare capacity.
        if (remaining == 0) return 0;
        int granted = Math.min(remaining, Math.min(perRequest, maximum));
        remaining -= granted;
        grants.put(key, 0);
        // Keep this caller at the tail for the next tick's round.
        waiting.remove(key);
        waiting.put(key, new Request(currentTick, maximum));
        return granted;
    }

    public void cancel(K key) {
        waiting.remove(key);
        // Never refund a reservation: callers may already have executed it.
        // Keep a consumed marker so cancel/restart cannot bypass the batch cap.
        grants.put(key, 0);
    }

    private void beginTick(long currentTick) {
        if (tick == currentTick) return;
        if (currentTick < tick) waiting.clear();
        tick = currentTick;
        remaining = perTick;
        grants.clear();
        waiting.entrySet().removeIf(entry -> currentTick - entry.getValue().tick() > staleTicks);
        Iterator<Map.Entry<K, Request>> iterator = waiting.entrySet().iterator();
        while (remaining > 0 && iterator.hasNext()) {
            Map.Entry<K, Request> entry = iterator.next();
            int grant = Math.min(remaining, Math.min(perRequest, entry.getValue().maximum()));
            remaining -= grant;
            grants.put(entry.getKey(), grant);
            iterator.remove();
        }
    }
}
