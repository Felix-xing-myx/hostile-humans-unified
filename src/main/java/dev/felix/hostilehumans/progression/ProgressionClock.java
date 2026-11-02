package dev.felix.hostilehumans.progression;

/** Saturating online-time arithmetic; no world calendar or saved offset. */
public final class ProgressionClock {
    private ProgressionClock() { }
    public static long addClamped(long base, long delta) {
        if (delta > 0L && base > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return Math.max(0L, base + delta);
    }
}
