package dev.felix.hostilehumans.behavior.combat.defense;

/** Cooldown starts when an actual pressure guard ends, never while waiting for a drink. */
final class PressureDefenseCycle {
    private int guardSince = Integer.MIN_VALUE;
    private int cooldownUntil = Integer.MIN_VALUE;
    boolean update(int now, boolean pressured, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return false; }
        if (guardSince != Integer.MIN_VALUE && (now - guardSince >= config.maxGuardTicks()
                || now - guardSince >= 16 && (now - guardSince) % 4 == 0 && !pressured)) {
            ended(now, config);
            return true;
        }
        if (cooldownUntil != Integer.MIN_VALUE && now >= cooldownUntil) cooldownUntil = Integer.MIN_VALUE;
        return false;
    }
    void guarded(int now) { if (!attempting() && guardSince == Integer.MIN_VALUE) guardSince = now; }
    void ended(int now, ShieldPressureConfig config) {
        if (guardSince == Integer.MIN_VALUE) return;
        int duration = Math.max(0, now - guardSince);
        guardSince = Integer.MIN_VALUE;
        cooldownUntil = now + Math.min(config.cooldownTicks(), 10 + (duration + 1) / 2);
    }
    int duration(int now) { return guardSince == Integer.MIN_VALUE ? 0 : now - guardSince; }
    int remainingCooldown(int now) { return attempting() ? Math.max(0, cooldownUntil - now) : 0; }
    boolean attempting() { return cooldownUntil != Integer.MIN_VALUE; }
    void clear() { guardSince = cooldownUntil = Integer.MIN_VALUE; }
}
