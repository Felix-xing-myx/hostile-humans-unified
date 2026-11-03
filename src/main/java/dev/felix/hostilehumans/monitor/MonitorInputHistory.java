package dev.felix.hostilehumans.monitor;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded client intent history. Only acknowledgements of simulated input retire entries. */
public final class MonitorInputHistory<T> {
    public static final int CAPACITY = 32, MAX_REPLAY = 8;
    public record Entry<T>(long sequence, T input) {}
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();
    private long lastSequence = -1, acknowledged = -1;
    private long droppedThrough = -1;

    public void add(long sequence, T input) {
        if (sequence <= lastSequence) throw new IllegalArgumentException("Non-monotonic monitor input");
        var entry = new Entry<>(sequence, java.util.Objects.requireNonNull(input));
        lastSequence = sequence;
        if (entries.size() == CAPACITY) droppedThrough = entries.removeFirst().sequence();
        entries.addLast(entry);
    }
    public void acknowledge(long sequence) {
        if (sequence < acknowledged) return;
        acknowledged = sequence;
        while (!entries.isEmpty() && entries.peekFirst().sequence() <= sequence) entries.removeFirst();
    }
    /** Empty means a valid empty replay; null means a discontinuity requiring an authoritative reset. */
    public List<Entry<T>> replay() {
        if (droppedThrough > acknowledged || entries.size() > MAX_REPLAY) { discard(); return null; }
        return List.copyOf(entries);
    }
    public void discard() { entries.clear(); droppedThrough = -1; }
    public void clear() { discard(); lastSequence = acknowledged = -1; }
    public int size() { return entries.size(); }
}
