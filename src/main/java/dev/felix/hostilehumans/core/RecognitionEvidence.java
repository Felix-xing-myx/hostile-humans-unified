package dev.felix.hostilehumans.core;

/** Per-target evidence. A missed scheduling slot is not a negative observation. */
public final class RecognitionEvidence {
    public static final int MAX_CONCURRENT = 2;
    private long started = -1, lastVisible = -1, missingSince = -1;
    private double progress;
    private boolean paused;

    public boolean active() { return started >= 0&&!paused; }
    public boolean hasEvidence(){return started>=0;}
    public void pause(){if(hasEvidence())paused=true;}
    public void resume(long now){if(!hasEvidence())begin(now);else paused=false;}
    public double progress() { return progress; }
    public void begin(long now) { reset(); started = now; }
    public void miss(long now) { if (hasEvidence() && missingSince < 0) missingSince = now; }
    public boolean interrupted(long now, int breakTicks) {
        return hasEvidence() && missingSince >= 0 && now - missingSince >= breakTicks;
    }
    public boolean timedOut(long now, int limit) { return active() && now - started >= limit; }
    public boolean visible(long now, int interval, int required, int breakTicks) {
        if (!active()) resume(now);
        if (interrupted(now, breakTicks)) { progress = 0; lastVisible = -1; }
        long elapsed = lastVisible < 0 ? 0 : now - lastVisible;
        // Never backfill a budget gap or a period containing an actual negative sample.
        long credit = missingSince < 0 && elapsed > 0 && elapsed <= interval ? elapsed : 1;
        missingSince = -1;
        lastVisible = now;
        progress = Math.min(1, progress + credit / (double) Math.max(1, required));
        return progress >= 1;
    }
    public void finish() { started = -1; missingSince = -1; paused=false; }
    public void reset() { started = lastVisible = missingSince = -1; progress = 0; paused=false; }
}
