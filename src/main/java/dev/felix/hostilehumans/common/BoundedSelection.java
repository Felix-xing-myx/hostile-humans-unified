package dev.felix.hostilehumans.common;

import java.util.*;

/** Visits every spatial-query result while retaining only a bounded best subset. */
public final class BoundedSelection<T> {
    private final int limit;
    private final Comparator<T> order;
    private final PriorityQueue<T> items;
    public BoundedSelection(int limit, Comparator<T> order) {
        this.limit = Math.max(1, limit); this.order = order;
        items = new PriorityQueue<>(this.limit, order.reversed());
    }
    public void offer(T item) {
        if (items.size() < limit) items.offer(item);
        else if (order.compare(item, items.peek()) < 0) { items.poll(); items.offer(item); }
    }
    public List<T> values() { List<T> result = new ArrayList<>(items); result.sort(order); return result; }
}
