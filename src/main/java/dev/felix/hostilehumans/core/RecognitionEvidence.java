package dev.felix.hostilehumans.core;

/** Per-target evidence. A missed scheduling slot is not a negative observation. */
public final class RecognitionEvidence {
    public static final int MAX_CONCURRENT = 2;
    private long started = -1, lastVisible = -1, missingSince = -1;
    private double progress;

    public boolean active() { return started >= 0; }
    public double progress() { return progress; }
    public void begin(long now) { reset(); started = now; }
    public void miss(long now) { if (active() && missingSince < 0) missingSince = now; }
    public boolean interrupted(long now, int breakTicks) {
        return active() && missingSince >= 0 && now - missingSince >= breakTicks;
    }
    public boolean timedOut(long now, int limit) { return active() && now - started >= limit; }
    public boolean visible(long now, int interval, int required, int breakTicks) {
        if (!active()) begin(now);
        if (interrupted(now, breakTicks)) { progress = 0; lastVisible = -1; }
        long elapsed = lastVisible < 0 ? 0 : now - lastVisible;
        // Never backfill a budget gap or a period containing an actual negative sample.
        long credit = missingSince < 0 && elapsed > 0 && elapsed <= interval ? elapsed : 1;
        missingSince = -1;
        lastVisible = now;
        progress = Math.min(1, progress + credit / (double) Math.max(1, required));
        return progress >= 1;
    }
    public void finish() { started = -1; missingSince = -1; }
    public void reset() { started = lastVisible = missingSince = -1; progress = 0; }
}
