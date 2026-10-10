package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests production arithmetic: discrete upgrades, exact bonus boundary, configuration and remainders. */
public final class GrowthProgressionTest {
    private static int checks;
    public static void main(String[] args) {
        GrowthConfig config = new UnifiedConfig(new JsonObject()).growth();
        double[] damage = {0, 9.99, 10, 12, 20, 22, 100, 159.99, 160, 300, 300.01, 10000};
        double[] rewards = {0, 0, .25, .25, .5, .5, 2.5, 3.75, 4, 4, 8, 8};
        for (int i = 0; i < damage.length; i++) {
            check(GrowthProgression.reward(damage[i], damage[i] > config.bonusDamageThreshold(),
                    config.damageUnit(), config.rewardUnit(), config.baseRewardCap(), config.bonusReward()) == rewards[i],
                    "reward boundary " + damage[i]);
        }
        var result = GrowthProgression.advance(config, 0, 100, 0, 0, 39.75);
        check(result.level() == 0 && result.stageProgress() == 39.75, "no fractional stage attributes");
        check(result.experience() == 109 && result.experienceProgress() == 4.75, "experience advances per one point");
        result = GrowthProgression.advance(config, result.level(), result.experience(), result.stageProgress(), result.experienceProgress(), .25);
        check(result.level() == 1 && result.stageProgress() == 0, "exact level threshold");
        check(result.experience() == 110 && result.experienceProgress() == 0, "two independent progress balances");
        result = GrowthProgression.advance(config, 0, 90, 0, 0, 201);
        check(result.level() == 5 && result.stageProgress() == 0, "multiple levels and no hoarding at cap");
        check(result.experience() == 121 && result.experienceProgress() == 15, "remainder survives multiple experience gains");
        result = GrowthProgression.advance(config, 4, 124, 39, 11, 8);
        check(result.level() == 5 && result.experience() == 125 && result.stageProgress() == 0 && result.experienceProgress() == 0, "both caps discard excess");
        result = GrowthProgression.advance(config, 1, 100, 20, 4, Double.NaN);
        check(result.level() == 1 && result.stageProgress() == 20, "nonfinite rewards cannot poison balances");
        GrowthConfig custom = new UnifiedConfig(JsonParser.parseString("""
                {"growth":{"stage_cost":[1,2,3,4,5],"experience":{"cost_by_band":[0.5,0.5,0.5,0.5,0.5],"cap_percent":120}}}
                """).getAsJsonObject()).growth();
        result = GrowthProgression.advance(custom, 0, 110, 0, 0, 7.25);
        check(result.level() == 3 && result.stageProgress() == 1.25 && result.experience() == 120, "custom stage costs and experience cap");
        result = GrowthProgression.advance(custom, 2, 100, 3, 0, 0);
        check(result.level() == 3 && result.stageProgress() == 0, "lowered threshold consumes saved progress without new reward");
        check(Math.abs(100 * config.stagePercent(2) / 100 * 107 / 100.0 - 74.9) < 1E-8, "stage then experience preserves fractional health");
        int[] starts = {90, 99, 100, 104, 105, 109, 110, 114, 115, 119, 120, 124};
        double[] costs = {3, 3, 3, 3, 5, 5, 8, 8, 12, 12, 16, 16};
        for (int i = 0; i < starts.length; i++) {
            check(config.experienceCost(starts[i]) == costs[i], "band cost " + starts[i]);
            result = GrowthProgression.advance(config, 5, starts[i], 0, 0, costs[i] - .25);
            check(result.experience() == starts[i], "below next-point threshold");
            result = GrowthProgression.advance(config, 5, starts[i], 0, result.experienceProgress(), .25);
            check(result.experience() == starts[i] + 1 && result.experienceProgress() == 0, "exact next-point threshold");
        }
        result = GrowthProgression.advance(config, 5, 104, 0, 0, 8);
        check(result.experience() == 106 && result.experienceProgress() == 0, "cross-band cost recomputed after every point");
        result = GrowthProgression.advance(config, 5, 100, 0, 0, 220);
        check(result.experience() == 125 && result.experienceProgress() == 0, "full training costs 220 points from 100%");
        System.out.println("GrowthProgressionTest: " + checks + " checks passed");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
