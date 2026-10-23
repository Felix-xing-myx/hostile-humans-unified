package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Incoming health-loss DPS and independent execution timers. */
public record ShieldPressureConfig(boolean enabled, int dpsSmoothingTicks, double dpsRatio,
        int quietTicks, int maxGuardTicks, int cooldownTicks) {
    static ShieldPressureConfig parse(JsonObject json) {
        return new ShieldPressureConfig(UnifiedConfig.bool(json, "enabled", true),
                (int) UnifiedConfig.number(json, "dps_smoothing_ticks", 10, 1, 100),
                UnifiedConfig.number(json, "dps_ratio", .22, .001, 10),
                (int) UnifiedConfig.number(json, "quiet_ticks", 20, 5, 200),
                (int) UnifiedConfig.number(json, "max_guard_ticks", 120, 1, 2400),
                (int) UnifiedConfig.number(json, "cooldown_ticks", 70, 1, 400));
    }
}
