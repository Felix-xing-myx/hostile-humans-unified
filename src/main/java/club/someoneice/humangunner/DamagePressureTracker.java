package club.someoneice.humangunner;

/** Event-driven exponentially smoothed incoming health-loss DPS, with O(1) state.
 * No minimum hit count, completed-window settlement or division by inter-hit time. */
final class DamagePressureTracker {
    private double rate;
    private int lastSampleTick = Integer.MIN_VALUE;
    private int smoothingTicks;
    private int lastPressureTick = Integer.MIN_VALUE;

    void record(int now, float damage, float maximumHealth, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return; }
        if (!(damage > 0) || !Float.isFinite(damage)
                || !(maximumHealth > 0) || !Float.isFinite(maximumHealth)) return;
        decay(now, config);
        rate += (double) damage / maximumHealth * 20.0 / config.dpsSmoothingTicks();
        if (active(now, config) || rate >= config.dpsRatio()) lastPressureTick = now;
    }

    private void decay(int now, ShieldPressureConfig config) {
        if (lastSampleTick != Integer.MIN_VALUE
                && (now < lastSampleTick || smoothingTicks != config.dpsSmoothingTicks())) clear();
        if (lastSampleTick != Integer.MIN_VALUE)
            rate *= Math.exp(-((long) now - lastSampleTick) / (double) config.dpsSmoothingTicks());
        lastSampleTick = now;
        smoothingTicks = config.dpsSmoothingTicks();
    }

    /** Diagnostic fraction of maximum health per second; never offensive knowledge. */
    double damageRatioPerSecond(int now, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return 0; }
        decay(now, config);
        return rate;
    }

    /** Actual guard may begin after a protected drink outlives the original damage. */
    void executingEvent(int now, ShieldPressureConfig config) {
        if (config.enabled()) lastPressureTick = now;
    }
    void blocked(int now, ShieldPressureConfig config) {
        if (active(now, config)) lastPressureTick = now;
    }
    boolean active(int now, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return false; }
        return lastPressureTick != Integer.MIN_VALUE && now >= lastPressureTick
                && now - lastPressureTick < config.quietTicks();
    }
    void clear() {
        rate = 0; lastSampleTick = Integer.MIN_VALUE;
        smoothingTicks = 0; lastPressureTick = Integer.MIN_VALUE;
    }
}
