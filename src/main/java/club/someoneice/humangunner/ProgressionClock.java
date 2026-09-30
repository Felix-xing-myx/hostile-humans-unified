package club.someoneice.humangunner;

/** A persistent calendar offset: follows the Overworld, including skipped nights. */
final class ProgressionClock {
    static final long TICKS_PER_DAY = 24000L;
    private long offset;

    ProgressionClock(long offset) {
        this.offset = offset;
    }

    long ticksAt(long overworldDayTime) {
        return addClamped(Math.max(0L, overworldDayTime), offset);
    }

    void setTicks(long value, long overworldDayTime) {
        offset = Math.max(0L, value) - Math.max(0L, overworldDayTime);
    }

    void setDay(int day, long overworldDayTime) {
        setTicks((Math.max(1L, day) - 1L) * TICKS_PER_DAY, overworldDayTime);
    }

    void addDays(int days, long overworldDayTime) {
        setTicks(addClamped(ticksAt(overworldDayTime), (long) days * TICKS_PER_DAY), overworldDayTime);
    }

    void syncFromOverworld() { offset = 0L; }

    long savedOffset() { return offset; }

    private static long addClamped(long base, long delta) {
        if (delta > 0L && base > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return Math.max(0L, base + delta);
    }
}
