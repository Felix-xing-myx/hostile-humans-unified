package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Per-rank decisions for bounded low-health combat; all durations use server ticks. */
public record LowHealthCombatConfig(
        double criticalHealthRatio,
        double criticalAbsoluteHealth,
        double finishChance,
        int trialTicks,
        int observationTicks,
        int decisionHoldTicks,
        int retryDelayTicks,
        double healingSafetyMultiplier
) {
    static LowHealthCombatConfig defaults() {
        return new LowHealthCombatConfig(0.15D, 25.0D, 0.5D, 40, 80, 80, 100, 1.1D);
    }

    static LowHealthCombatConfig parse(JsonObject root, double retreatHealthRatio) {
        LowHealthCombatConfig defaults = defaults();
        return new LowHealthCombatConfig(
                Math.min(retreatHealthRatio, UnifiedConfig.number(root, "critical_health_ratio",
                        defaults.criticalHealthRatio, 0.01D, 0.30D)),
                UnifiedConfig.number(root, "critical_absolute_health", defaults.criticalAbsoluteHealth, 1.0D, 1024.0D),
                UnifiedConfig.number(root, "finish_chance", defaults.finishChance, 0.0D, 1.0D),
                ticks(root, "trial_ticks", defaults.trialTicks, 20, 200),
                ticks(root, "observation_ticks", defaults.observationTicks, 20, 400),
                ticks(root, "decision_hold_ticks", defaults.decisionHoldTicks, 20, 400),
                ticks(root, "retry_delay_ticks", defaults.retryDelayTicks, 20, 1200),
                UnifiedConfig.number(root, "healing_safety_multiplier", defaults.healingSafetyMultiplier, 1.0D, 3.0D)
        );
    }

    private static int ticks(JsonObject root, String key, int fallback, int min, int max) {
        return (int) UnifiedConfig.number(root, key, fallback, min, max);
    }
}
