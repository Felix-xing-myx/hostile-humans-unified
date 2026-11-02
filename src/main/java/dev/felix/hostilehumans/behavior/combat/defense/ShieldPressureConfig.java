package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.JsonObject;

/** Incoming health-loss DPS, execution timers and active final-damage mitigation. */
public record ShieldPressureConfig(boolean enabled, int dpsSmoothingTicks, double dpsRatio,
        int quietTicks, int maxGuardTicks, int cooldownTicks, double damageReduction) {
    public ShieldPressureConfig {
        if (!Double.isFinite(damageReduction) || damageReduction < 0 || damageReduction > 1)
            throw new IllegalArgumentException("Pressure guard damage reduction must be within 0..1");
    }
    /** Preserve existing timer-only callers with the same mitigation default. */
    public ShieldPressureConfig(boolean enabled, int smoothing, double ratio, int quiet, int maximum, int cooldown) {
        this(enabled, smoothing, ratio, quiet, maximum, cooldown, .20);
    }
    public double damageMultiplier(boolean executing) { return enabled && executing ? 1 - damageReduction : 1; }
    public static ShieldPressureConfig parse(JsonObject json) {
        return new ShieldPressureConfig(UnifiedConfig.bool(json, "enabled", true),
                (int) UnifiedConfig.number(json, "dps_smoothing_ticks", 10, 1, 100),
                UnifiedConfig.number(json, "dps_ratio", .22, .001, 10),
                (int) UnifiedConfig.number(json, "quiet_ticks", 20, 5, 200),
                (int) UnifiedConfig.number(json, "max_guard_ticks", 120, 1, 2400),
                (int) UnifiedConfig.number(json, "cooldown_ticks", 70, 1, 400),
                UnifiedConfig.number(json, "damage_reduction", .20, 0, 1));
    }
}
