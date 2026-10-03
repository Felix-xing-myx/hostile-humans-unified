package club.someoneice.humangunner;

import java.util.Map;

/** Spawn-only policy. Day changes never upgrade or downgrade a living soldier. */
record EquipmentGrowthPolicy(boolean enabled, Map<String, Profile> tiers) {
    record Profile(int startDay, int fullQualityDay, double earlyBaselineChance,
            double earlyGunChanceMultiplier, double earlyEnchantmentKeepChance,
            double earlyEnchantmentLevelMultiplier, double earlyTotemKeepChance,
            double earlyTridentKeepChance, String spartanMaterial,
            Map<String, String> earlyMelee, Map<String, String> earlyArmor) {
        double progress(long day) {
            // Equal/inverted dates mean baseline gear from the configured end day.
            if (day >= fullQualityDay) return 1.0D;
            if (day <= startDay || fullQualityDay <= startDay) return 0.0D;
            return (double) (day - startDay) / (fullQualityDay - startDay);
        }

        double grow(double early, long day) {
            return early + (1.0D - early) * progress(day);
        }
    }
}
