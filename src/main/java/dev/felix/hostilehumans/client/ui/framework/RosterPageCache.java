package dev.felix.hostilehumans.client.ui.framework;

import java.util.LinkedHashMap;
import java.util.List;

/** One screen/token owns one bounded cache; cached rows never grant mutation permission. */
public final class RosterPageCache<T> {
    private final int capacity;
    private int tier = Integer.MIN_VALUE, squad = Integer.MIN_VALUE;
    private final LinkedHashMap<Integer, List<T>> pages = new LinkedHashMap<>(8, .75F, true);

    public RosterPageCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("positive page capacity");
        this.capacity = capacity;
    }

    public void filter(int tier, int squad) {
        if (this.tier != tier || this.squad != squad) {
            clear();
            this.tier = tier;
            this.squad = squad;
        }
    }

    public void put(int page, List<T> values) {
        pages.put(page, List.copyOf(values));
        while (pages.size() > capacity) pages.remove(pages.keySet().iterator().next());
    }

    public List<T> get(int page) {
        return pages.getOrDefault(page, List.of());
    }

    public void clear() {
        pages.clear();
    }

    public int size() {
        return pages.size();
    }
}
