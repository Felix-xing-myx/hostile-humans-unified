package club.someoneice.humangunner;

/** Server-local bounded guard/trial cycle; only fresh damaging hits can fail a trial. */
final class PressureDefenseCycle {
    private int guardSince = Integer.MIN_VALUE;
    private int attemptUntil = Integer.MIN_VALUE;
    private final RecoveryPressureWindow attemptDamage = new RecoveryPressureWindow();

    /** Returns true once when a successful trial retires the previous pressure history. */
    boolean update(int now, boolean pressured, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return false; }
        if (attemptUntil != Integer.MIN_VALUE) {
            if (now < attemptUntil) return false;
            clear();
            return true;
        }
        if (!pressured) { guardSince = Integer.MIN_VALUE; return false; }
        if (guardSince != Integer.MIN_VALUE && now - guardSince >= config.maxGuardTicks()) {
            attemptUntil = now + config.breakAttemptTicks();
            guardSince = Integer.MIN_VALUE;
            attemptDamage.clear();
        }
        return false;
    }

    void guarded(int now) {
        if (!attempting() && guardSince == Integer.MIN_VALUE) guardSince = now;
    }

    void damage(int now, float damage, float maximumHealth, ShieldPressureConfig config) {
        if (!attempting() || now >= attemptUntil) return;
        attemptDamage.record(now, damage, maximumHealth, config);
        if (attemptDamage.active(now, config)) clear(); // Resume forced defense, with a fresh guard timer.
    }

    boolean attempting() { return attemptUntil != Integer.MIN_VALUE; }
    void clear() {
        guardSince = Integer.MIN_VALUE;
        attemptUntil = Integer.MIN_VALUE;
        attemptDamage.clear();
    }
}
