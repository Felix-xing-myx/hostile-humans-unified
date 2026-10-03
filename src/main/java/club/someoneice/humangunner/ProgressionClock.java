package club.someoneice.humangunner;

/** Saturating online-time arithmetic; no world calendar or saved offset. */
final class ProgressionClock {
    private ProgressionClock() { }
    static long addClamped(long base, long delta) {
        if (delta > 0L && base > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return Math.max(0L, base + delta);
    }
}
