package dev.felix.hostilehumans.core;

/** One tick's reusable value, invalidated immediately by the caller's mutation revision. */
public final class TickRevisionMemo<T> {
    private boolean present;
    private long tick;
    private int revision;
    private T value;

    public boolean isCurrent(long tick, int revision) {
        return present && this.tick == tick && this.revision == revision;
    }

    public T value() { return value; }

    public void remember(long tick, int revision, T value) {
        this.tick = tick;
        this.revision = revision;
        this.value = value;
        present = true;
    }
}
