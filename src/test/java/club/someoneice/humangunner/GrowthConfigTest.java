package club.someoneice.humangunner;

import com.google.gson.*;

/** Shared curve, global ownership and invalid-input regression checks. */
public final class GrowthConfigTest {
    public static void main(String[] args) {
        GrowthProgressionTest.main(args);
        GrowthPersistenceTest.main(args);
        UnifiedConfig config = new UnifiedConfig(new JsonObject());
        GrowthConfig growth = config.growth();
        check(growth.enabled(), "default enabled");
        check(growth.stagePercent(0) == 50 && growth.stagePercent(5) == 100, "stage attributes");
        for (int level = 0; level < 5; level++) check(growth.stageCost(level) == 40, "stage cost");
        check(growth.experienceMinPercent() == 90 && growth.experienceMaxPercent() == 110
                && growth.experienceCapPercent() == 125 && growth.experienceCost() == 12, "experience defaults");
        check(growth.damageUnit() == 10 && growth.rewardUnit() == .25 && growth.baseRewardCap() == 4
                && growth.bonusDamageThreshold() == 300 && growth.bonusReward() == 4, "combat defaults");
        int[] start = {3, 5, 10, 20}, duration = {37, 45, 60, 60};
        for (int i = 0; i < 4; i++) {
            String rank = TierConfigProjection.RANKS.get(i);
            check(growth.stageAt(rank, start[i]) == 0 && growth.stageAt(rank, start[i] + duration[i]) == 5, "curve endpoints " + rank);
            int previous = 0;
            for (long day = start[i]; day <= start[i] + duration[i]; day++) {
                int stage = growth.stageAt(rank, day);
                check(stage >= previous && stage <= 5, "bounded monotonic stage"); previous = stage;
            }
            check(growth.stageAt(rank, Long.MIN_VALUE) == 0 && growth.stageAt(rank, Long.MAX_VALUE) == 5, "long bounds");
        }
        // Compare pre-experience base health at every relevant default birth day.
        // Wild snapshots do not grow later, so monotonic curves also bound older lower-rank units.
        for (long day = 0; day <= 200; day++) {
            double lower = 0;
            for (String rank : TierConfigProjection.RANKS) {
                double health = config.tier(rank).maxHealth() * growth.stagePercent(growth.stageAt(rank, day)) / 100.0;
                check(health > lower, "higher rank has higher pre-experience birth health: day=" + day + " rank=" + rank);
                lower = health;
            }
        }
        JsonObject custom = json("""
                {"growth":{"profiles":{"roamer":{"start_day":10,"duration_days":100,"progress_nodes":[0.1,0.3,0.7,0.9]}}}}
                """);
        growth = new UnifiedConfig(custom).growth();
        check(growth.stageAt("roamer", 30) == 0 && growth.stageAt("roamer", 50) == 1
                && growth.stageAt("roamer", 70) == 3 && growth.stageAt("roamer", 90) == 4, "piecewise custom nodes");
        var profile = new UnifiedConfig(custom).equipmentGrowth().tiers().get("roamer");
        check(Math.abs(profile.progress(60) - .5) < 1e-12, "equipment shares curve interpolation");
        check(new UnifiedConfig(json("{\"growth\":{\"enabled\":false}}")).growth().stageAt("roamer", 1) == 0, "disabled retains latent birth stage");
        check(!new UnifiedConfig(json("{\"growth\":{\"enabled\":false}}")).equipmentGrowth().enabled(), "same equipment switch");
        for (boolean vanilla : new boolean[]{false, true}) {
            for (boolean configuredEnabled : new boolean[]{false, true}) {
                JsonObject settings = json("{\"vanilla_mode\":" + vanilla
                        + ",\"growth\":{\"enabled\":" + configuredEnabled + "}}");
                UnifiedConfig combination = new UnifiedConfig(settings);
                check(combination.vanillaMode() == vanilla, "growth cannot change vanilla mode");
                check(combination.growth().enabled() == (configuredEnabled && !vanilla), "vanilla mode takes precedence over growth");
                check(combination.equipmentGrowth().enabled() == combination.growth().enabled(), "equipment uses effective growth policy");
                check(combination.growth().stageAt("tier2", 10) == 0, "suppression retains latent birth stage");
                check(settings.getAsJsonObject("growth").get("enabled").getAsBoolean() == configuredEnabled, "effective override cannot rewrite saved growth preference");
                settings.addProperty("vanilla_mode", false);
                check(new UnifiedConfig(settings).growth().enabled() == configuredEnabled, "leaving vanilla mode restores configured preference");
            }
        }
        JsonObject bad = json("""
                {"growth":{"stage_percent":[50,60,60,80,90,100],"stage_cost":[0,40,40,40,40],
                "experience":{"min_percent":120,"max_percent":110,"cap_percent":125,"cost":-2},
                "combat":{"damage_unit":0},"profiles":{"roamer":{"duration_days":0,"progress_nodes":[0.4,0.2,0.6,0.8]}}}}
                """);
        growth = new UnifiedConfig(bad).growth();
        check(growth.stagePercent(2) == 70 && growth.stageCost(0) == 40, "bad arrays safe defaults without sorting");
        check(growth.experienceMinPercent() == 90 && growth.experienceCost() == 12 && growth.damageUnit() == 10, "invalid bounds safe defaults");
        check(growth.stageAt("roamer", 40) == 5, "invalid duration and curve use defaults");
        check(new UnifiedConfig(json("{\"growth\":{\"experience\":{\"min_percent\":90.5}}}")).growth().experienceMinPercent() == 90, "fractional percentage rejected");
        check(new UnifiedConfig(json("{\"growth\":null}")).growth().enabled(), "invalid group default");
        JsonObject defaults = UnifiedConfig.defaults(), global = TierConfigProjection.global(defaults);
        check(global.has("growth") && global.getAsJsonObject("growth").has("profiles"), "all growth in global");
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject tier = TierConfigProjection.tier(defaults, rank);
            check(!tier.has("growth") && !tier.has("equipment_growth"), "no rank growth");
            JsonObject attributes = tier.getAsJsonObject("attributes");
            check(attributes.has("max_health") && !attributes.has("health_min") && !attributes.has("health_max"), "single maximum health");
            tier.add("growth", json("{\"enabled\":false}"));
            check(!TierConfigProjection.arrange(tier, true).has("growth"), "rank cannot override global growth");
        }
        System.out.println("GrowthConfigTest passed");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
