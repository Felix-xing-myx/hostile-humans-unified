package dev.felix.hostilehumans.common;

/** A tactical search episode survives geometry restarts; granted work is distinct from waiting. */
public final class BoundedSearchCycle {
    public enum Outcome { OPEN, DEFERRED, EXHAUSTED }
    public static final int MAX_TICKS = 160, MIN_WORK = 32, MAX_WORK = 128;
    private final int maximumTicks, minimumWork, maximumWork;
    private long started = Long.MIN_VALUE;
    private int work;
    public BoundedSearchCycle() { this(MAX_TICKS, MIN_WORK, MAX_WORK); }
    public BoundedSearchCycle(int ticks, int minimum, int maximum) {
        if (ticks <= 0 || minimum <= 0 || maximum < minimum) throw new IllegalArgumentException("invalid search cycle");
        maximumTicks = ticks; minimumWork = minimum; maximumWork = maximum;
    }
    public void begin(long tick) {
        if (started == Long.MIN_VALUE || tick < started) { started = tick; work = 0; }
    }
    public void work(int credits) { if (credits > 0) work = (int) Math.min(maximumWork, (long) work + credits); }
    public int remainingWork() { return maximumWork - work; }
    public Outcome outcome(long tick) {
        if (started == Long.MIN_VALUE || tick < started) return Outcome.OPEN;
        if (work >= maximumWork) return Outcome.EXHAUSTED;
        return tick - started >= maximumTicks ? work >= minimumWork ? Outcome.EXHAUSTED : Outcome.DEFERRED : Outcome.OPEN;
    }
    public void reset() { started = Long.MIN_VALUE; work = 0; }
}
