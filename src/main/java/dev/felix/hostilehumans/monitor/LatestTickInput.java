package dev.felix.hostilehumans.monitor;

/** Single-slot input coalescer. Offers replace stale queued intent; the simulation drains once per tick. */
public final class LatestTickInput<T> {
    private final int maximumAge;
    private T pending;
    private long receivedAt;
    private boolean received;
    public LatestTickInput(int maximumAge) {
        if (maximumAge < 0) throw new IllegalArgumentException("maximum input age");
        this.maximumAge = maximumAge;
    }
    public void offer(T input, long tick) {
        pending = java.util.Objects.requireNonNull(input); receivedAt = tick; received = true;
    }
    public T drain() { T input = pending; pending = null; return input; }
    public boolean fresh(long now) { return received && now >= receivedAt && now - receivedAt >= 0 && now - receivedAt <= maximumAge; }
}
