package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Server-owned pursuit attack settings, independent of ordinary movement hops. */
public record JumpAttackConfig(boolean enabled, double damageMultiplier, double chance,
                               int intervalMinTicks, int intervalMaxTicks,
                               double approachExtra, double maxHeightDifference) {
    static JumpAttackConfig parse(JsonObject data) {
        int min = (int) UnifiedConfig.number(data, "attempt_interval_min_ticks", 12, 1, 1200);
        int max = (int) UnifiedConfig.number(data, "attempt_interval_max_ticks", 22, 1, 1200);
        return new JumpAttackConfig(UnifiedConfig.bool(data, "enabled", true),
                UnifiedConfig.number(data, "damage_multiplier", 1.5, 0, 10),
                UnifiedConfig.number(data, "chance", 0.6, 0, 1), Math.min(min, max), Math.max(min, max),
                UnifiedConfig.number(data, "max_approach_distance_extra", 1.5, 0, 4),
                UnifiedConfig.number(data, "max_target_height_difference", 0.75, 0, 2));
    }
}
