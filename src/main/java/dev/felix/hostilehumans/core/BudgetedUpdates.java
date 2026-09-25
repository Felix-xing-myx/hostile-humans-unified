package dev.felix.hostilehumans.core;

import java.util.*;

/** FIFO coalescing queue. Drain detaches a bounded batch before consumers run. */
public final class BudgetedUpdates<K> {
    private final LinkedHashSet<K> pending = new LinkedHashSet<>();
    public void offer(K key) { pending.add(Objects.requireNonNull(key)); }
    public List<K> drain(int budget) {
        if (budget < 0) throw new IllegalArgumentException("negative budget");
        List<K> batch = new ArrayList<>(Math.min(budget, pending.size()));
        Iterator<K> iterator = pending.iterator();
        while (iterator.hasNext() && batch.size() < budget) {
            batch.add(iterator.next());
            iterator.remove();
        }
        return List.copyOf(batch);
    }
    public int size() { return pending.size(); }
}
