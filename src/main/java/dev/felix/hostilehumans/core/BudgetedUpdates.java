// SPDX-License-Identifier: GPL-2.0-only
// Copyright (c) 2026 Felix-xing-myx
package dev.felix.hostilehumans.core;

import java.util.*;

/** FIFO coalescing queue. Drain detaches a bounded batch before consumers run. */
public final class BudgetedUpdates<K> {
    private final LinkedHashSet<K> pending = new LinkedHashSet<>();
    public void offer(K key) { pending.add(Objects.requireNonNull(key)); }
    public List<K> drain(int budget) {
        if (budget < 0) throw new IllegalArgumentException("negative budget");
        if (budget == 0 || pending.isEmpty()) return List.of();
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
