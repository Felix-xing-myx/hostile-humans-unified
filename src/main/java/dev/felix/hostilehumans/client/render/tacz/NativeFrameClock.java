package dev.felix.hostilehumans.client.render.tacz;

/** Observation-local render clock, independent of 20 Hz packets and paused gameTime. */
final class NativeFrameClock {
    private long previous = Long.MIN_VALUE;
    private double seconds;

    double sample(long now, boolean paused) {
        if (previous != Long.MIN_VALUE && now > previous && !paused)
            seconds += (now - previous) / 1_000_000_000D;
        previous = now;
        return seconds;
    }
}
