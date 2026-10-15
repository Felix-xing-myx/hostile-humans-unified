package dev.felix.hostilehumans.core;

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
    public K cycle() {
        List<K> keys = new ArrayList<>(slots.keySet());
        focused = keys.isEmpty() ? null : keys.get((keys.indexOf(focused) + 1) % keys.size());
        return focused;
    }
    public boolean contains(K key) { return slots.containsKey(key); }
    public K focused() { return focused; }
    public boolean isEmpty() { return slots.isEmpty(); }
    public List<Entry<K>> entries() {
        return slots.entrySet().stream().map(entry -> new Entry<>(entry.getKey(), entry.getValue())).toList();
    }
}
