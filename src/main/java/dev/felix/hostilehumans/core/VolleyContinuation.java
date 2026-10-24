package dev.felix.hostilehumans.core;

/** One bounded continuation. Waiting never renews its lifetime or grants execution authority. */
public final class VolleyContinuation {
    public enum State { PENDING, COMPLETED, CANCELLED, EXPIRED }
    private final long expires;
    private final int retryTicks;
    private long nextAttempt;
    private State state = State.PENDING;

    public VolleyContinuation(long due, int maximumWaitTicks, int retryTicks) {
        if (due < 0 || maximumWaitTicks < 1 || retryTicks < 1)
            throw new IllegalArgumentException("volley continuation timing");
        nextAttempt = due;
        expires = after(due, maximumWaitTicks);
        this.retryTicks = retryTicks;
    }

    public boolean canAttempt(long now) {
        if (state != State.PENDING) return false;
        if (now >= expires) { state = State.EXPIRED; return false; }
        return now >= nextAttempt;
    }

    /** Only a successfully inserted volley completes the continuation; blocked attempts retain it. */
    public void attempted(long now, boolean published) {
        if (state != State.PENDING) return;
        if (published) state = State.COMPLETED;
        else if (now >= expires) state = State.EXPIRED;
        else nextAttempt = after(now, retryTicks);
    }

    public void cancel() { if (state == State.PENDING) state = State.CANCELLED; }
    public State state() { return state; }
    public boolean terminal() { return state != State.PENDING; }
    private static long after(long now, int ticks) { return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks; }
}
