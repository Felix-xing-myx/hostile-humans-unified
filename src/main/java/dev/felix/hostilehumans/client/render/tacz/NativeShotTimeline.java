package dev.felix.hostilehumans.client.render.tacz;

/**
 * One native shot track. Restart at zero on an accepted shot and fade its authored tail to idle.
 */
final class NativeShotTimeline {
    record Frame(double seconds, float weight) {}

    private double start = Double.NEGATIVE_INFINITY;

    void shot(double now) {
        start = now;
    }

    double elapsed(double now) {
        return now - start;
    }

    Frame sample(double now, double duration) {
        double elapsed = elapsed(now);
        if (!Double.isFinite(elapsed) || elapsed < 0 || duration <= 0 || elapsed >= duration)
            return null;
        double tail = Math.min(.04, duration * .2);
        float weight = (float) Math.min(1, (duration - elapsed) / tail);
        return new Frame(elapsed, weight * weight * (3 - 2 * weight));
    }
}
