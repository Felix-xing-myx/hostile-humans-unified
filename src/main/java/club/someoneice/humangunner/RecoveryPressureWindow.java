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
        int longest = Math.max(config.windowTicks(), config.frequentWindowTicks());
        while (!hits.isEmpty() && now - hits.peekFirst().tick() >= longest) hits.removeFirst();
        if (hits.size() >= 256) hits.removeFirst();
        hits.addLast(new Hit(now, damage));
        double recent = 0, frequent = 0;
        int recentCount = 0, frequentCount = 0;
        for (Hit hit : hits) {
            if (now - hit.tick() < config.windowTicks()) { recent += hit.damage(); recentCount++; }
            if (now - hit.tick() < config.frequentWindowTicks()) { frequent += hit.damage(); frequentCount++; }
        }
        if (active(now, config) || damage >= maximumHealth * config.singleDamageRatio()
                || (recentCount >= 2 && recent >= maximumHealth * config.damageRatio())
                || (frequentCount >= config.frequentHits() && frequent >= maximumHealth * config.frequentDamageRatio()))
            lastPressureTick = now;
    }

    /** Actual executing pressure guard may have begun after a protected drink outlived the samples. */
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

    void clear() { hits.clear(); lastPressureTick = Integer.MIN_VALUE; }
}
