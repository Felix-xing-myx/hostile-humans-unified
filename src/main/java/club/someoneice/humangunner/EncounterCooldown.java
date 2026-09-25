package club.someoneice.humangunner;

/** Per-dimension admission clock. Failed attempts never advance the clock. */
final class EncounterCooldown {
    static final long DURATION = 2400L;
    private final long duration;
    EncounterCooldown() { this(DURATION); }
    EncounterCooldown(long duration) { this.duration = Math.max(0, duration); }
    private long next = Long.MIN_VALUE;
    private long batchTick = Long.MIN_VALUE;
    private Object batch;
    private int members;

    boolean cooling(long tick) {
        return tick < next;
    }

    boolean join(long tick, Object token, int limit) {
        if (token == null || limit < 1) return false;
        boolean sameBatch = batch == token && batchTick == tick;
        if (sameBatch ? members >= limit : cooling(tick)) return false;
        if (!sameBatch) {
            batch = token;
            batchTick = tick;
            members = 0;
        }
        members++;
        next = tick + duration;
        return true;
    }
}
