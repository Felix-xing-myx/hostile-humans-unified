package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Shieldable health-loss thresholds and execution timers. */
public record ShieldPressureConfig(boolean enabled, int windowTicks, double damageRatio,
        int frequentHits, double frequentDamageRatio, int quietTicks,
        int maxGuardTicks, int cooldownTicks, int frequentWindowTicks, double singleDamageRatio) {
    /** Source compatibility for integrations using the original constructor. */
    public ShieldPressureConfig(boolean enabled, int windowTicks, double damageRatio, int frequentHits,
            double frequentDamageRatio, int quietTicks, int maxGuardTicks, int cooldownTicks) {
        this(enabled, windowTicks, damageRatio, frequentHits, frequentDamageRatio,
                quietTicks, maxGuardTicks, cooldownTicks, 30, .35);
    }
    @Deprecated public int breakAttemptTicks() { return cooldownTicks; }
    static ShieldPressureConfig parse(JsonObject json) {
        return new ShieldPressureConfig(UnifiedConfig.bool(json, "enabled", true),
                (int) UnifiedConfig.number(json, "window_ticks", 20, 5, 100),
                UnifiedConfig.number(json, "damage_ratio", .18, .01, 1),
                (int) UnifiedConfig.number(json, "frequent_hits", 5, 2, 64),
                UnifiedConfig.number(json, "frequent_damage_ratio", .10, .001, 1),
                (int) UnifiedConfig.number(json, "quiet_ticks", 20, 5, 200),
                (int) UnifiedConfig.number(json, "max_guard_ticks", 120, 1, 2400),
                (int) UnifiedConfig.number(json, "cooldown_ticks", 40, 1, 400),
                (int) UnifiedConfig.number(json, "frequent_window_ticks", 30, 5, 200),
                UnifiedConfig.number(json, "single_damage_ratio", .35, .01, 1));
    }
}
