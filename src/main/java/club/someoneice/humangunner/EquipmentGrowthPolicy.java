package club.someoneice.humangunner;

import java.util.Map;
import java.util.List;

/** Spawn-only policy. Day changes never upgrade or downgrade a living soldier. */
record EquipmentGrowthPolicy(boolean enabled, Map<String, Profile> tiers) {
    record Profile(int startDay, int fullQualityDay, List<Double> progressNodes, double earlyBaselineChance,
            double earlyGunChanceMultiplier, double earlyEnchantmentKeepChance,
            double earlyEnchantmentLevelMultiplier, double earlyTotemKeepChance,
            double earlyTridentKeepChance, String spartanMaterial,
            Map<String, String> earlyMelee, Map<String, String> earlyArmor) {
        double progress(long day) {
            if (day >= fullQualityDay) return 1.0D;
            if (day <= startDay) return 0.0D;
            double elapsed = (double) (day - startDay) / (fullQualityDay - startDay);
            // Four configurable progress values at 20%, 40%, 60%, 80% of duration.
            double segment = elapsed * 5.0D;
            int index = Math.min(4, (int) segment);
            double left = index == 0 ? 0.0D : progressNodes.get(index - 1);
            double right = index == 4 ? 1.0D : progressNodes.get(index);
            return left + (right - left) * (segment - index);
        }

        double grow(double early, long day) {
            return early + (1.0D - early) * progress(day);
        }
    }
}
