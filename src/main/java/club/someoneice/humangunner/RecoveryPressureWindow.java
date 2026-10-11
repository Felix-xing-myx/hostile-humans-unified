package club.someoneice.humangunner;

import java.util.ArrayDeque;

/** Bounded per-entity damage samples. Never drives retreat or scans the world. */
final class RecoveryPressureWindow {
    private record Hit(int tick, float damage) { }
    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private int lastPressureTick = Integer.MIN_VALUE;

    void record(int now, float damage, float maximumHealth, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return; }
        if (!(damage > 0) || !Float.isFinite(damage) || !(maximumHealth > 0)) return;
        while (!hits.isEmpty() && now - hits.peekFirst().tick() > config.windowTicks()) hits.removeFirst();
        if (hits.size() >= 256) hits.removeFirst();
        hits.addLast(new Hit(now, damage));
        double total = 0;
        for (Hit hit : hits) total += hit.damage();
        if (active(now, config) || (hits.size() >= 2 && total >= maximumHealth * config.damageRatio())
                || (hits.size() >= config.frequentHits() && total >= maximumHealth * config.frequentDamageRatio()))
            lastPressureTick = now;
    }

    void blocked(int now, ShieldPressureConfig config) {
        if (active(now, config)) lastPressureTick = now;
    }

    boolean active(int now, ShieldPressureConfig config) {
        if (!config.enabled()) { clear(); return false; }
        return lastPressureTick != Integer.MIN_VALUE && now >= lastPressureTick
                && now - lastPressureTick < config.quietTicks();
    }

    void clear() { hits.clear(); lastPressureTick = Integer.MIN_VALUE; }
}
