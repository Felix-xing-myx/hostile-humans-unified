package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Global server rules for choosing defense over item recovery. */
public record ShieldPressureConfig(boolean enabled, int windowTicks, double damageRatio,
                                   int frequentHits, double frequentDamageRatio, int quietTicks,
                                   int maxGuardTicks, int breakAttemptTicks) {
    static ShieldPressureConfig parse(JsonObject json) {
        return new ShieldPressureConfig(UnifiedConfig.bool(json, "enabled", true),
                (int) UnifiedConfig.number(json, "window_ticks", 20, 5, 100),
                UnifiedConfig.number(json, "damage_ratio", .15, .01, 1),
                (int) UnifiedConfig.number(json, "frequent_hits", 4, 2, 64),
                UnifiedConfig.number(json, "frequent_damage_ratio", .05, .001, 1),
                (int) UnifiedConfig.number(json, "quiet_ticks", 20, 5, 200),
                (int) UnifiedConfig.number(json, "max_guard_ticks", 140, 40, 2400),
                (int) UnifiedConfig.number(json, "break_attempt_ticks", 60, 10, 400));
    }
}
