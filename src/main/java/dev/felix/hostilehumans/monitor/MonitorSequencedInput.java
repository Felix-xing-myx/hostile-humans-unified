package dev.felix.hostilehumans.monitor;

/** Server inbox: receiving an intent is distinct from simulating it. */
public final class MonitorSequencedInput<T> {
    public record Intent<T>(long sequence, T input) {}
    private final LatestTickInput<Intent<T>> pending;
    private long received = -1, acknowledged = -1;
    public MonitorSequencedInput(int maximumAge) { pending = new LatestTickInput<>(maximumAge); }
    public boolean offer(long sequence, T input, long tick) {
        if (sequence < 0 || sequence <= received) return false;
        received = sequence; pending.offer(new Intent<>(sequence, input), tick); return true;
    }
    public T drain() {
        var intent = pending.drain();
        if (intent == null) return null;
        acknowledged = intent.sequence(); return intent.input();
    }
    public long acknowledged() { return acknowledged; }
    public boolean fresh(long tick) { return pending.fresh(tick); }
}
