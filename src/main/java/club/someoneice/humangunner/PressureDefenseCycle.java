package club.someoneice.humangunner;

/** Cooldown starts when an actual pressure guard ends, never while waiting for a drink. */
final class PressureDefenseCycle {
    private int guardSince = Integer.MIN_VALUE;
    private int cooldownUntil = Integer.MIN_VALUE;
    boolean update(int now, boolean pressured, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return false; }
        if (guardSince != Integer.MIN_VALUE && (!pressured || now - guardSince >= config.maxGuardTicks())) {
            ended(now, config);
            return true;
        }
        if (cooldownUntil != Integer.MIN_VALUE && now >= cooldownUntil) cooldownUntil = Integer.MIN_VALUE;
        return false;
    }
    void guarded(int now) { if (!attempting() && guardSince == Integer.MIN_VALUE) guardSince = now; }
    void ended(int now, ShieldPressureConfig config) {
        if (guardSince == Integer.MIN_VALUE) return;
        guardSince = Integer.MIN_VALUE;
        cooldownUntil = now + config.cooldownTicks();
    }
    /** Damage during cooldown cannot shorten the mandatory interval. */
    void damage(int now, float damage, float maximumHealth, ShieldPressureConfig config) { }
    boolean attempting() { return cooldownUntil != Integer.MIN_VALUE; }
    void clear() { guardSince = cooldownUntil = Integer.MIN_VALUE; }
}
