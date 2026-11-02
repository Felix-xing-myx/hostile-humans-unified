package dev.felix.hostilehumans.behavior;

import java.util.EnumMap;
import java.util.function.Predicate;

/** Exact execution owner per delayed movement input channel. No priorities or clocks are duplicated here. */
public final class InputExecutionLease<K extends Enum<K>, T> {
    private final EnumMap<K, T> sources;
    public InputExecutionLease(Class<K> channels) { sources = new EnumMap<>(channels); }
    public T source(K channel) { return sources.get(channel); }
    public boolean request(K channel, T token, boolean gated, Predicate<T> live) {
        if (!gated) return true;
        if (token == null || !live.test(token)) return false;
        sources.put(channel, token); return true;
    }
    public boolean clear(K channel, T caller, boolean gated, Predicate<T> live) {
        T owner = sources.get(channel);
        if (gated) {
            if (owner != null && !owner.equals(caller) && live.test(owner)) return false;
            if (owner == null && (caller == null || !live.test(caller))) return false;
        }
        sources.remove(channel); return true;
    }
    public boolean live(K channel, boolean gated, Predicate<T> live) {
        T owner = sources.get(channel);
        return !gated || owner != null && live.test(owner);
    }
    public void discard(K channel) { sources.remove(channel); }
    public void cancel() { sources.clear(); }
}
