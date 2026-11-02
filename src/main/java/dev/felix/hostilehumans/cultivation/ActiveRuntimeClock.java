package dev.felix.hostilehumans.cultivation;

/**
 * Thread-safe local pause observations use the same monotonic timeline, never wall-calendar elapsed
 * time.
 */
public final class ActiveRuntimeClock {
    private long millis, nanosRemainder, anchor = Long.MIN_VALUE;
    private boolean paused;

    public ActiveRuntimeClock(long persistedMillis) {
        if (persistedMillis < 0) throw new IllegalArgumentException();
        millis = persistedMillis;
    }

    public synchronized long advance(long nanoTime) {
        if (anchor != Long.MIN_VALUE && nanoTime > anchor && !paused) {
            long elapsed = Math.addExact(nanoTime - anchor, nanosRemainder);
            millis = Math.addExact(millis, elapsed / 1_000_000);
            nanosRemainder = elapsed % 1_000_000;
        }
        if (anchor == Long.MIN_VALUE || nanoTime > anchor) anchor = nanoTime;
        return millis;
    }

    public synchronized void paused(long nanoTime, boolean value) {
        advance(nanoTime);
        paused = value;
    }

    public synchronized long current() {
        return millis;
    }

    public synchronized void stop() {
        anchor = Long.MIN_VALUE;
        nanosRemainder = 0;
        paused = false;
    }
}
