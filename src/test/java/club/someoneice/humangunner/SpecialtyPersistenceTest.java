package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

/** Requires Minecraft test runtime, but no live entity or server. */
public final class SpecialtyPersistenceTest {
    public static void main(String[] args) {
        CompoundTag current = new CompoundTag();
        double[] values = SpecialtyAttributes.baseline(); values[0] = .85; values[9] = 1.25;
        SpecialtyAttributes.store(current, 1.05, values, 123456);
        CompoundTag before = current.copy();
        check(SpecialtyAttributes.retainSaved(current), "existing payload recognized");
        check(current.equals(before), "reload does not reroll");
        close(SpecialtyAttributes.storedMultiplier(current, SpecialtyAttributes.Dimension.HEALTH), .85, "saved health");
        close(SpecialtyAttributes.storedMultiplier(current, SpecialtyAttributes.Dimension.ACCURACY), 1.25, "positive accuracy");

        CompoundTag legacy = new CompoundTag();
        ListTag ten = new ListTag();
        for (int i = 0; i < 10; i++) ten.add(FloatTag.valueOf(i == 9 ? .8F : 1));
        legacy.put("multipliers", ten); legacy.putInt("allocation_schema", 1); legacy.putFloat("budget", 1);
        legacy.putInt("experience_percent", 117); legacy.putLong("allocation_seed", 99);
        check(SpecialtyAttributes.retainSaved(legacy), "explicit legacy recognized");
        check(legacy.getInt("allocation_schema") == 3 && legacy.getList("multipliers", Tag.TAG_FLOAT).size() == 13, "legacy expanded");
        close(SpecialtyAttributes.storedMultiplier(legacy, SpecialtyAttributes.Dimension.ACCURACY), 1.25, "legacy spread converted once");
        close(SpecialtyAttributes.storedMultiplier(legacy, SpecialtyAttributes.Dimension.REGENERATION), 1, "legacy regeneration baseline");
        check(legacy.getInt("experience_percent") == 117 && legacy.getLong("allocation_seed") == 99, "other growth preserved");
        CompoundTag migrated = legacy.copy(); SpecialtyAttributes.retainSaved(legacy);
        check(legacy.equals(migrated), "idempotent migration");

        CompoundTag eleven = new CompoundTag();
        ListTag old = new ListTag();
        for(int i=0;i<11;i++)old.add(FloatTag.valueOf((float)(.8+i*.025)));
        eleven.put("multipliers",old);eleven.putInt("allocation_schema",2);eleven.putFloat("budget",1.12F);
        SpecialtyAttributes.retainSaved(eleven);
        for(int i=0;i<11;i++)close(eleven.getList("multipliers",Tag.TAG_FLOAT).getFloat(i),old.getFloat(i),"old value exact");
        close(SpecialtyAttributes.storedMultiplier(eleven, SpecialtyAttributes.Dimension.ATTACK_SPEED),1,"old speed one");
        close(SpecialtyAttributes.storedMultiplier(eleven, SpecialtyAttributes.Dimension.ARMOR_PENETRATION),0,"old penetration zero");
        for (int schema : new int[]{0, 99}) {
            CompoundTag future = new CompoundTag(); future.putInt("allocation_schema", schema);
            future.put("multipliers", ten.copy()); future.putString("future_metadata", "keep me");
            CompoundTag exact = future.copy();
            check(SpecialtyAttributes.retainSaved(future) && future.equals(exact), "unknown payload preserved");
            close(SpecialtyAttributes.storedMultiplier(future, SpecialtyAttributes.Dimension.ACCURACY), 1, "unknown effective baseline");
        }
        CompoundTag invalid = current.copy(); invalid.getList("multipliers", Tag.TAG_FLOAT).set(4, FloatTag.valueOf(Float.NaN));
        CompoundTag exact = invalid.copy(); SpecialtyAttributes.retainSaved(invalid);
        check(invalid.equals(exact), "invalid payload retained");
        close(SpecialtyAttributes.storedMultiplier(invalid, SpecialtyAttributes.Dimension.HEALTH), 1, "whole invalid vector neutralized");

        SpecialtyConfig defaults = SpecialtyConfig.defaults();
        close(defaults.budgetMean(), 1.1, "new base mean");
        close(defaults.deviationSigma(), .2, "wider log-normal variation");
        close(defaults.deviationCap(), .45, "ordinary tilt cap");
        close(defaults.budgetMax(), 1.4, "new base truncation maximum");
        var bossDefaults = FactionRules.parse(new com.google.gson.JsonObject());
        close(bossDefaults.bossBudget(defaults, "tier1"), 1.54, "tier-one boss");
        close(bossDefaults.bossBudget(defaults, "tier2"), 1.694, "tier-two boss");
        close(bossDefaults.bossBudget(defaults, "tier3"), 1.848, "tier-three boss");
        close(bossDefaults.allocationMaximum(defaults), 2.1, "boss-only allocation ceiling");
        close(bossDefaults.bossBases().get("health"), 1.8, "reduced independent boss health");
        close(bossDefaults.bossBases().get("melee"), 1.15, "reduced independent boss melee");
        var staleBoss = FactionRules.parse(com.google.gson.JsonParser.parseString(
                "{\"boss\":{\"budget\":0.8,\"allocation_amplification\":3}}").getAsJsonObject());
        close(staleBoss.bossBudget(defaults, "tier3"), 1.848, "obsolete fixed budget cannot defeat rank guarantee");
        var customBoss = FactionRules.parse(com.google.gson.JsonParser.parseString(
                "{\"boss\":{\"budget_multiplier\":1.2,\"allocation_multiplier_max\":2.3}}").getAsJsonObject());
        var customSpecialty = SpecialtyConfig.prepare(com.google.gson.JsonParser.parseString(
                "{\"budget_max\":1.6,\"budget_tier_multipliers\":{\"tier2\":1.4}}").getAsJsonObject());
        close(customBoss.bossBudget(customSpecialty, "tier2"), 1.6 * 1.4 * 1.2, "custom rank and base ceiling are respected");
        close(customBoss.allocationMaximum(customSpecialty), 2.3, "custom boss ceiling");

        var prepared = SpecialtyConfig.prepare(new com.google.gson.JsonObject());
        for (String tier : new String[]{"roamer", "tier1", "tier2", "tier3"}) {
            double factor = switch (tier) { case "tier2" -> 1.1; case "tier3" -> 1.2; default -> 1; };
            close(prepared.tierBudgetMultiplier(tier), factor, "prepared default tier factor");
            for (long seed = 0; seed < 128; seed++) {
                double base = SpecialtyAttributes.sampleBudget(RandomSource.create(seed), defaults);
                double scaled = SpecialtyAttributes.sampleBudget(RandomSource.create(seed), defaults, tier);
                close(scaled, base * factor, "same draw is multiplied after truncation");
                check(scaled >= .8 * factor && scaled <= 1.4 * factor, "tier-specific range");
            }
        }
        double topThird = SpecialtyBudgetSampler.sample(Math.nextDown(1.0), 1.1, .1, .8, 1.4)
                * defaults.tierBudgetMultiplier("tier3");
        check(topThird > 1.67 && topThird <= 1.68, "tier budget is not clamped back to the base range");
        var customData = com.google.gson.JsonParser.parseString(
                "{\"budget_tier_multipliers\":{\"tier2\":1.3}}").getAsJsonObject();
        close(SpecialtyConfig.prepare(customData).tierBudgetMultiplier("tier2"), 1.3, "custom tier factor");
        close(SpecialtyConfig.prepare(customData).tierBudgetMultiplier("tier3"), 1.2, "missing tier keeps default");
        for (String bad : new String[]{"-1", "0", "4", "\"NaN\""}) {
            var invalidData = com.google.gson.JsonParser.parseString(
                    "{\"budget_tier_multipliers\":{\"tier2\":" + bad + "}}").getAsJsonObject();
            boolean rejected = false;
            try { SpecialtyConfig.prepare(invalidData); } catch (RuntimeException expected) { rejected = true; }
            check(rejected, "invalid tier factor rejected");
        }
        checkTierBudgetFeasibility(defaults);
        RandomSource random = RandomSource.create(123);
        double total = 0; int high = 0;
        for (int i = 0; i < 100000; i++) {
            double budget = SpecialtyAttributes.sampleBudget(random, defaults);
            check(budget >= .8 && budget <= 1.4, "truncated budget bounds");
            total += budget; if (budget > 1.15) high++;
        }
        check(Math.abs(total / 100000 - 1.1) < .002, "budget mean");
        check(Math.abs(high / 100000.0 - .3080) < .006, "resampling distribution");
        var degenerate = new SpecialtyConfig(true, 2, 0, .75, 1.25, 0, .3, .25, .5, 1.8);
        close(SpecialtyAttributes.sampleBudget(random, degenerate), 1.25, "bounded sigma-zero fallback");
        System.out.println("SpecialtyPersistenceTest: all checks passed");
    }
    /** Real bundled definitions: spend feasible budgets and retain explicit residual above bounds. */
    private static void checkTierBudgetFeasibility(SpecialtyConfig config) {
        var dimensions = resource("data/hostile_humans_unified/specialty/dimensions.json");
        var definitions = SpecialtyData.parse(java.util.Map.of(
                new net.minecraft.resources.ResourceLocation(HumanGunner.MOD_ID, "dimensions"), dimensions));
        close(definitions.caps(config.deviationCap())[7], .3, "gun tilt cap");
        close(definitions.caps(config.deviationCap())[4], .2, "speed retains explicit twenty-percent cap");
        close(definitions.caps(config.deviationCap())[9], .5, "accuracy retains explicit cap");
        for (String faction : new String[]{"crimson", "azure", "verdant", "ashen"}) {
            var raw = resource("data/hostile_humans_unified/factions/" + faction + ".json")
                    .getAsJsonObject().getAsJsonArray("allocation_preference");
            double[] preference = new double[SpecialtyAttributes.DIMENSIONS];
            for (int i = 0; i < preference.length; i++) preference[i] = raw.get(i).getAsDouble();
            var rootConfig = resource("defaults/hostile_humans_unified.json").getAsJsonObject();
            var bossRules = FactionRules.parse(rootConfig);
            for (String tier : new String[]{"tier1", "tier2", "tier3"}) {
                double ordinaryCeiling = config.budgetMax() * config.tierBudgetMultiplier(tier);
                double bossBudget = bossRules.bossBudget(config, tier);
                close(bossBudget, ordinaryCeiling * 1.10, "boss budget follows actual rank ceiling");
                check(bossBudget > ordinaryCeiling, "boss target budget exceeds every ordinary draw of its rank");
                for (double tilt : new double[]{.1, 1, 10}) {
                    double[] tilts = SpecialtyAttributes.ones();
                    for (int i = 0; i < tilts.length; i++) tilts[i] = (i & 1) == 0 ? tilt : 1 / tilt;
                    var result = SpecialtyAllocation.allocate(bossBudget, preference, definitions.costs(),
                            definitions.caps(config.deviationCap()), tilts, config.capBudgetExponent(),
                            config.multiplierMin(), bossRules.allocationMaximum(config));
                    check(result.feasible() && Math.abs(result.residual()) < 1e-7,
                            "actual boss budget fits without unspent residual: " + faction + "/" + tier);
                    for (int i = 0; i < result.multipliers().length; i++)
                        check(result.multipliers()[i] <= (i == 12 ? .45 : 2.1) + 1e-9,
                                "boss-specific ceiling retains penetration cap");
                }
            }
            for (int step = 0; step <= 88; step++) {
                double budget = .8 + step * .01;
                for (double tilt : new double[]{.1, 1, 10}) {
                    double[] tilts = SpecialtyAttributes.ones();
                    for (int i = 0; i < tilts.length; i++) tilts[i] = (i & 1) == 0 ? tilt : 1 / tilt;
                    var result = SpecialtyAllocation.allocate(budget, preference, definitions.costs(),
                            definitions.caps(config.deviationCap()), tilts, config.capBudgetExponent(),
                            config.multiplierMin(), config.multiplierMax());
                    double minimum = 0, maximum = 0;
                    double[] costs = definitions.costs();
                    for (int i = 0; i < costs.length; i++) {
                        minimum += costs[i] * result.lower()[i]; maximum += costs[i] * result.upper()[i];
                        check(result.multipliers()[i] >= result.lower()[i] - 1e-9
                                        && result.multipliers()[i] <= result.upper()[i] + 1e-9,
                                "narrow speed cap cannot be bypassed to spend a high budget");
                    }
                    double target = budget * SpecialtyAllocation.baselineCost(costs);
                    boolean feasible = target >= minimum - 1e-12 && target <= maximum + 1e-12;
                    check(result.feasible() == feasible, "feasibility follows merged bounds");
                    check(Math.abs(result.residual() - (Math.max(minimum, Math.min(maximum, target)) - target)) < 1e-7,
                            "infeasible budgets retain explicit residual instead of violating caps");
                    if (budget <= 1.6) check(feasible, "built-in factions retain full allocation through 1.6");
                    for (int i = 0; i < result.multipliers().length; i++)
                        check(result.multipliers()[i] <= (i == 12 ? .45 : 1.8) + 1e-9,
                                "expanded relative bounds retain absolute ceilings");
                }
            }
        }
    }
    private static com.google.gson.JsonElement resource(String path) {
        try (var stream = SpecialtyPersistenceTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing resource " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static void close(double actual, double expected, String message) { check(Math.abs(actual-expected)<1e-6, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
