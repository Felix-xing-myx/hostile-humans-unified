package dev.felix.hostilehumans.observer;

import java.util.*;

/** Stable display slots; focusing an existing member never removes or reorders another member. */
public final class NavigationObserverSelection<K> {
    public record Entry<K>(K key, int slot) {}
    private final int maximum;
    private final LinkedHashMap<K, Integer> slots = new LinkedHashMap<>();
    private K focused;
    public NavigationObserverSelection(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }
    /** Restore stable colors and insertion order, ignoring corrupt/duplicate saved entries. */
    public void restore(List<Entry<K>> entries, K focus) {
        slots.clear();
        for (var entry : entries) {
            if (slots.size() >= maximum) break;
            if (entry == null || entry.key() == null || entry.slot() < 0 || entry.slot() >= maximum
                    || slots.containsKey(entry.key()) || slots.containsValue(entry.slot())) continue;
            slots.put(entry.key(), entry.slot());
        }
        focused = slots.containsKey(focus) ? focus : slots.isEmpty() ? null : slots.keySet().iterator().next();
    }
    public boolean add(K key) {
        Objects.requireNonNull(key);
        if (slots.containsKey(key)) { focused = key; return true; }
        if (slots.size() >= maximum) return false;
        int slot = 0;
        while (slots.containsValue(slot)) slot++;
        slots.put(key, slot); focused = key; return true;
    }
    public boolean remove(K key) {
        if (slots.remove(key) == null) return false;
        if (Objects.equals(key, focused)) focused = slots.isEmpty() ? null : slots.keySet().iterator().next();
        return true;
    }
    public K cycle() { return cycle(key -> true); }
    /** Pending/unloaded members keep their slots, but need not intercept active focus cycling. */
    public K cycle(java.util.function.Predicate<? super K> available) {
        List<K> keys = slots.keySet().stream().filter(available).toList();
        if (!keys.isEmpty()) focused = keys.get((keys.indexOf(focused) + 1) % keys.size());
        return focused;
    }
    public boolean contains(K key) { return slots.containsKey(key); }
    public K focused() { return focused; }
    public boolean isEmpty() { return slots.isEmpty(); }
    public List<Entry<K>> entries() {
        return slots.entrySet().stream().map(entry -> new Entry<>(entry.getKey(), entry.getValue())).toList();
    }
}
