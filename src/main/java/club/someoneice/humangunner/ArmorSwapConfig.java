package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Full-quality upgrades and condition-based replacements are separate decisions. */
public record ArmorSwapConfig(double upgradeGain, double upgradeCondition, double lowCondition,
        double conditionAdvantage, double emergencyCondition, double emergencyQuality, int cooldownTicks) {
    static ArmorSwapConfig parse(JsonObject data) {
        double low = percent(data, "replace_below_durability_percent", 25);
        return new ArmorSwapConfig(percent(data, "upgrade_min_gain_percent", 10),
                percent(data, "upgrade_min_durability_percent", 50), low,
                percent(data, "replacement_durability_advantage_percent", 25),
                Math.min(low, percent(data, "emergency_below_durability_percent", 10)),
                percent(data, "emergency_min_quality_percent", 80),
                (int) UnifiedConfig.number(data, "cooldown_ticks", 200, 0, 12000));
    }

    private static double percent(JsonObject data, String key, double fallback) {
        return UnifiedConfig.number(data, key, fallback, 0, 100) / 100.0D;
    }

    boolean shouldReplace(boolean empty, double quality, double condition,
                          double candidateQuality, double candidateCondition, boolean cooling) {
        if (!Double.isFinite(candidateQuality) || candidateQuality <= 0
                || !Double.isFinite(candidateCondition) || candidateCondition <= 0) return false;
        if (empty) return true;
        if (!Double.isFinite(quality) || !Double.isFinite(condition)) return false;
        boolean fresher = candidateCondition > condition
                && candidateCondition + 1.0E-9 >= condition + conditionAdvantage;
        // An emergency bypasses the timer, but never swaps to an equally worn piece.
        if (condition <= emergencyCondition && fresher
                && candidateQuality >= quality * emergencyQuality) return true;
        if (cooling) return false;
        if (candidateQuality > quality && candidateQuality + 1.0E-9 >= quality * (1 + upgradeGain)
                && candidateCondition >= upgradeCondition) return true;
        return condition <= lowCondition && fresher && candidateQuality >= quality;
    }
}
