package dev.felix.hostilehumans.behavior.combat.ranged;

/** Execution evidence, not enemy knowledge or a movement controller. All durations use active observations. */
final class RangedEngagementProgress {
    static final int RELAX_TICKS = 30, BLOCKED_TICKS = 100, STALLED_TICKS = 80, FAILURES = 3, DEFER_TICKS = 100;
    private int last = -1, failures, lastFailure = -10000, blocked, stalled, deferredUntil = -1;
    private double x, y, z;
    void observe(int tick, double px, double py, double pz, boolean canFire) {
        if (last > tick) reset();
        int delta = last >= 0 && tick - last <= 5 ? Math.min(2, tick - last) : 1;
        boolean continuous = last >= 0 && tick - last <= 5;
        double dx = px - x, dy = py - y, dz = pz - z;
        boolean moved = last >= 0 && dx * dx + dy * dy + dz * dz >= .09;
        if (!continuous || moved) { x = px; y = py; z = pz; }
        last = tick;
        if (canFire) { failures = blocked = stalled = 0; deferredUntil = -1; return; }
        blocked = Math.min(1000, blocked + Math.max(0, delta));
        if (moved) { stalled = failures = 0; deferredUntil = -1; }
        else stalled = Math.min(1000, stalled + Math.max(0, delta));
        reconsider(tick);
    }
    void failure(int tick) {
        if (last < 0 || tick < last || tick - last > 5 || tick - lastFailure < 10) return;
        failures = Math.min(8, failures + 1); lastFailure = tick;
        reconsider(tick);
    }
    private void reconsider(int tick) {
        if (failures >= FAILURES && blocked >= BLOCKED_TICKS && stalled >= STALLED_TICKS && !deferred(tick)) {
            deferredUntil = tick + DEFER_TICKS;
            failures = 0; // Consumed evidence cannot renew a cooldown without new execution failures.
        }
    }
    boolean relax() { return blocked >= RELAX_TICKS; }
    boolean deferred(int tick) { return last >= 0 && tick >= last && tick < deferredUntil; }
    private void reset() { last = -1; failures = blocked = stalled = 0; lastFailure = -10000; deferredUntil = -1; }
}
