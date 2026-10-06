package club.someoneice.humangunner;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class UnifiedConfigTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        List<String> gameplaySections = defaults.keySet().stream().filter(key -> !key.startsWith("_")).toList();
        check(gameplaySections.get(gameplaySections.size()-1).equals("tacz"), "TaCZ last gameplay section, ignoring guidance metadata");
        UnifiedConfig config = new UnifiedConfig(new JsonObject());
        check(!config.vanillaMode(), "vanilla mode defaults off for existing configs");
        checkEquipmentLoadouts(config);
        int[] defaultPayments = {8, 24, 72, 216};
        for (int tier = 0; tier < 4; tier++) {
            check(config.recruitmentCost(tier).itemId().equals("minecraft:emerald")
                    && config.recruitmentCost(tier).count() == defaultPayments[tier], "unchanged default hiring payment");
        }
        JsonObject paymentOverride = JsonParser.parseString("{\"recruitment\":{\"payment_by_tier\":{\"tier1\":{\"item\":\"create:brass_ingot\",\"count\":37}}}}").getAsJsonObject();
        check(new UnifiedConfig(paymentOverride).recruitmentCost(1).equals(
                new UnifiedConfig.RecruitmentCost("create:brass_ingot", 37)), "custom mod item and quantity");
        check(new UnifiedConfig(paymentOverride).recruitmentCost(2).count() == 72, "other tiers preserve defaults");
        JsonObject customPayment = paymentOverride.getAsJsonObject("recruitment").getAsJsonObject("payment_by_tier").getAsJsonObject("tier1");
        for (String invalid : List.of("-1", "1.5", "1000001", "\"12\"", "null")) {
            customPayment.add("count", JsonParser.parseString(invalid));
            check(new UnifiedConfig(paymentOverride).recruitmentCost(1) == null, "invalid payment quantity fails closed");
        }
        customPayment.addProperty("count", 0);
        check(new UnifiedConfig(paymentOverride).recruitmentCost(1).count() == 0, "explicit zero payment");
        customPayment.addProperty("item", "not an id");
        check(new UnifiedConfig(paymentOverride).recruitmentCost(1) == null, "malformed payment ID fails closed");
        check(config.betterCombatSoldierMeleeEnabled(), "soldier Better Combat defaults on");
        JsonObject betterCombatOverride = new JsonObject();
        betterCombatOverride.add("better_combat", JsonParser.parseString(
                "{\"soldier_melee_enabled\":true}"
        ));
        check(new UnifiedConfig(betterCombatOverride).betterCombatSoldierMeleeEnabled(),
                "soldier Better Combat option can be enabled");
        check(config.taczEnabled(), "auto enable when installed");
        check(config.tier("roamer").healthMin() == 50, "legacy health min");
        check(config.tier("tier3").healthMax() == 100, "legacy health max");
        check(config.tier("roamer").baseMovementSpeed() == .105D
                        && config.tier("tier3").baseMovementSpeed() == .105D,
                "new per-tier base speed defaults preserve current movement");
        check(config.tier("roamer").gunSpreadDegrees("rifle") == 1.4D
                        && config.tier("tier1").gunSpreadDegrees("rifle") == 1.1D
                        && config.tier("tier2").gunSpreadDegrees("rifle") == 0.8D
                        && config.tier("tier3").gunSpreadDegrees("rifle") == 0.5D,
                "per-tier rifle spread defaults");
        Map<String, Double> firearmTierSteps = Map.of(
                "sniper", 0.2D, "rifle", 0.3D, "mg", 0.4D, "pistol", 0.4D,
                "smg", 0.4D, "shotgun", 0.5D, "other", 0.3D);
        for (String type : GunSpreadPolicy.supportedTypes()) {
            double previous = Double.POSITIVE_INFINITY;
            for (String tier : List.of("roamer", "tier1", "tier2", "tier3")) {
                double spread = config.tier(tier).gunSpreadDegrees(type);
                if (previous != Double.POSITIVE_INFINITY) {
                    check(Math.abs(previous - spread - firearmTierSteps.get(type)) < 1.0E-9D,
                            type + " uses its configured per-tier spread step");
                }
                previous = spread;
            }
        }
        check(config.tier("tier3").gunSpreadDegrees("sniper") == 0.2D
                        && config.tier("tier3").gunSpreadDegrees("rifle") == 0.5D
                        && config.tier("tier3").gunSpreadDegrees("mg") == 0.8D
                        && config.tier("tier3").gunSpreadDegrees("pistol") == 1.0D
                        && config.tier("tier3").gunSpreadDegrees("smg") == 1.2D
                        && config.tier("tier3").gunSpreadDegrees("shotgun") == 0.5D
                        && config.tier("tier3").gunSpreadDegrees("other") == 1.0D,
                "each weapon family reaches its intended top-tier accuracy floor");
        double previousBow = Double.POSITIVE_INFINITY;
        double previousCrossbow = Double.POSITIVE_INFINITY;
        for (String tier : List.of("roamer", "tier1", "tier2", "tier3")) {
            double bow = config.tier(tier).projectileSpreadDegrees("bow");
            double crossbow = config.tier(tier).projectileSpreadDegrees("crossbow");
            check(Math.abs(bow - crossbow - 0.3D) < 1.0E-9D,
                    "the default crossbow is 0.3 degrees more accurate than bow");
            if (previousBow != Double.POSITIVE_INFINITY) {
                check(Math.abs(previousBow - bow - 0.3D) < 1.0E-9D
                                && Math.abs(previousCrossbow - crossbow - 0.3D) < 1.0E-9D,
                        "bow and crossbow improve by 0.3 degrees per tier");
            }
            previousBow = bow;
            previousCrossbow = crossbow;
        }
        check(Math.abs(previousBow - 0.6D) < 1.0E-9D
                        && Math.abs(previousCrossbow - 0.3D) < 1.0E-9D,
                "top-tier bow reaches 0.6 degrees and crossbow reaches 0.3 degrees");
        check(config.tier("tier3").projectileSpreadDegrees("trident") == 1.5D,
                "trident has its own spread setting with the existing default");
        check(config.spawn().admissionChance() == .08, "legacy spawn chance");
        NaturalSpawnProgression defaultProgression = config.spawn().progression();
        check(defaultProgression.enabled() && defaultProgression.safeDays() == 1
                        && defaultProgression.roamerFirstDay() == 3 && defaultProgression.tier1FirstDay() == 5
                        && defaultProgression.tier2FirstDay() == 10 && defaultProgression.tier3FirstDay() == 20,
                "new world protection and rank dates default correctly");
        NaturalSpawnProgression boundedProgression = new UnifiedConfig(JsonParser.parseString("""
                {"spawning":{"progression":{"safe_days":-5,"first_spawn_day_by_tier":{
                "roamer":0,"tier1":1000001,"tier2":"invalid"}}}}
                """).getAsJsonObject()).spawn().progression();
        check(boundedProgression.safeDays() == 0 && boundedProgression.roamerFirstDay() == 1
                        && boundedProgression.tier1FirstDay() == 1000000 && boundedProgression.tier2FirstDay() == 10,
                "progression values clamp safely and invalid dates use defaults");
        check(config.tier("tier3").spawnMultiplier() == .04, "legacy rare tier");
        check(config.recruitmentLimit(0) == 12 && config.recruitmentLimit(1) == 10
                && config.recruitmentLimit(2) == 6 && config.recruitmentLimit(3) == 3,
                "legacy recruitment limits retained as defaults");
        check(!config.allowHiredPvpDamage(), "hired PvP damage defaults off");
        check(Math.abs(config.damage("human_bow_damage_multiplier", 0) / 1.44D - 0.84D) < 1.0E-9D
                        && Math.abs(config.damage("human_trident_damage_multiplier", 0) / 1.2D - 0.7D) < 1.0E-9D
                        && Math.abs(config.damage("trident_stab_damage_multiplier", 0) / 0.6D - 0.7D) < 1.0E-9D,
                "human arrows gain twenty percent from the prior default while trident damage stays unchanged");
        check(HumanGunnerConfig.parse(config.gunConfig()).humanGunDamageMultiplier() == 0.5D,
                "human firearm damage is half the TaCZ base before tier-specific scaling");

        JsonObject accuracyOverride = JsonParser.parseString("""
                {"tiers":{"tier2":{"weapon_spread_degrees":{
                  "firearms":{"sniper":4.25,"rifle":3.75,"pistol":2.25},
                  "projectiles":{"bow":6.5,"crossbow":7.25,"trident":8.0}}}}}
                """).getAsJsonObject();
        UnifiedConfig adjustableAccuracy = new UnifiedConfig(accuracyOverride);
        check(adjustableAccuracy.tier("tier2").gunSpreadDegrees("sniper") == 4.25D
                        && adjustableAccuracy.tier("tier2").gunSpreadDegrees("rifle") == 3.75D
                        && adjustableAccuracy.tier("tier2").gunSpreadDegrees("pistol") == 2.25D
                        && adjustableAccuracy.tier("tier2").gunSpreadDegrees("smg") == 1.6D
                        && adjustableAccuracy.tier("tier1").gunSpreadDegrees("rifle") == 1.1D
                        && adjustableAccuracy.tier("tier2").projectileSpreadDegrees("bow") == 6.5D
                        && adjustableAccuracy.tier("tier2").projectileSpreadDegrees("crossbow") == 7.25D
                        && adjustableAccuracy.tier("tier2").projectileSpreadDegrees("trident") == 8.0D,
                "each firearm and projectile type is independently adjustable without changing other values");
        JsonObject obsolete = JsonParser.parseString("""
                {"tiers":{"tier1":{"health_min":55,"normal_movement_speed":0.42,
                  "gun_spread_degrees":1.25,"projectile_spread_degrees":3.0}}}
                """).getAsJsonObject();
        check(new UnifiedConfig(obsolete).tier("tier1").equals(new UnifiedConfig(defaults).tier("tier1")),
                "obsolete flat attributes are not translated into current settings");

        UnifiedConfig movementConfig = new UnifiedConfig(JsonParser.parseString("""
                {"tiers":{"roamer":{"movement":{"base_speed":0.08}},
                 "tier2":{"movement":{"base_speed":0.11}}},
                 "ai":{"food_use_speed_multiplier":0.7,"shield_use_speed_multiplier":0.35}}
                """).getAsJsonObject());
        check(movementConfig.tier("roamer").baseMovementSpeed() == .08D
                        && movementConfig.tier("tier1").baseMovementSpeed() == .105D
                        && movementConfig.tier("tier2").baseMovementSpeed() == .11D,
                "new movement formula exposes independently adjustable tier base speeds");
        CombatAiConfig actionSpeeds = CombatAiConfig.parse(movementConfig.ai());
        check(actionSpeeds.foodUseSpeedMultiplier() == .7D
                        && actionSpeeds.shieldUseSpeedMultiplier() == .35D,
                "food and shield action speed multipliers are configurable");

        LowHealthCombatConfig lowHealthDefaults = CombatAiConfig.parse(new JsonObject()).lowHealthCombat();
        check(lowHealthDefaults.equals(new LowHealthCombatConfig(.15D, 25.0D, .5D, 40, 80, 80, 100, 1.1D)),
                "low-health combat defaults expose ratio and absolute forced-retreat thresholds");
        CombatAiConfig boundedLowHealth = CombatAiConfig.parse(JsonParser.parseString("""
                {"retreat_health_ratio":0.1,"low_health_combat":{
                    "critical_health_ratio":0.9,"critical_absolute_health":2000,"finish_chance":-1,
                    "trial_ticks":0,"observation_ticks":999,"decision_hold_ticks":0,
                    "retry_delay_ticks":9999,"healing_safety_multiplier":0}}
                """).getAsJsonObject());
        check(boundedLowHealth.lowHealthCombat().equals(
                        new LowHealthCombatConfig(.1D, 1024.0D, 0.0D, 20, 400, 20, 1200, 1.0D)),
                "nested low-health thresholds respect retreat ratio and all bounds");
        check(CombatAiConfig.parse(JsonParser.parseString("""
                {"low_health_combat":{"critical_health_ratio":0,"critical_absolute_health":0,
                    "finish_chance":2,"trial_ticks":999,"observation_ticks":0,
                    "decision_hold_ticks":999,"retry_delay_ticks":0,"healing_safety_multiplier":9}}
                """).getAsJsonObject()).lowHealthCombat().equals(
                        new LowHealthCombatConfig(.01D, 1.0D, 1.0D, 200, 20, 400, 20, 3.0D)),
                "low-health values clamp both lower and upper limits");
        check(CombatAiConfig.parse(JsonParser.parseString("{\"low_health_combat\":null}")
                        .getAsJsonObject()).lowHealthCombat().equals(lowHealthDefaults)
                        && CombatAiConfig.parse(JsonParser.parseString("{\"low_health_combat\":false}")
                        .getAsJsonObject()).lowHealthCombat().equals(lowHealthDefaults),
                "malformed nested settings use current defaults");
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject rankAi = TierConfigProjection.tier(defaults, rank).getAsJsonObject("ai");
            check(rankAi.has("low_health_combat")
                            && CombatAiConfig.parse(rankAi).lowHealthCombat().equals(lowHealthDefaults),
                    "each rank receives independently configurable low-health defaults: " + rank);
        }
        JsonObject lowHealthOverride = defaults.deepCopy();
        TierConfigProjection.put(lowHealthOverride, "tier_ai.tier2.low_health_combat.finish_chance", new JsonPrimitive(.8D));
        UnifiedConfig rankLowHealth = new UnifiedConfig(lowHealthOverride);
        check(rankLowHealth.combatAi("tier2").lowHealthCombat().finishChance() == .8D
                        && rankLowHealth.combatAi("tier1").lowHealthCombat().finishChance() == .5D,
                "rank low-health override does not alter other ranks");
        check(CombatAiConfig.parse(JsonParser.parseString("{\"recovery_damage_tolerance\":60}")
                        .getAsJsonObject()).recoveryDamageToleranceRatio() == 1.0D / 6.0D,
                "removed absolute recovery-damage alias cannot override current ratio defaults");
        check(CombatAiConfig.parse(JsonParser.parseString("{\"recovery_damage_tolerance_ratio\":0.3}")
                        .getAsJsonObject()).recoveryDamageToleranceRatio() == .3D,
                "current recovery-damage ratio remains configurable");

        JsonObject current = defaults.deepCopy();
        TierConfigProjection.put(current, "tacz.tier2_gun_chance", new JsonPrimitive(0.9));
        TierConfigProjection.put(current, "tacz.gun_whitelist", JsonParser.parseString("{\"test:keep\":7,\"test:zero\":0,\"banned:gun\":100,\"test:black\":2}"));
        TierConfigProjection.put(current, "tacz.excluded_gun_namespaces", JsonParser.parseString("[\"banned\"]"));
        TierConfigProjection.put(current, "tacz.gun_blacklist", JsonParser.parseString("[\"test:black\"]"));
        config = new UnifiedConfig(current);
        HumanGunnerConfig weapon = HumanGunnerConfig.parse(config.gunConfig());
        check(weapon.tier2GunChance() == .9, "current tier firearm chance");
        var pool = weapon.selectAvailable(Map.of(
                id("test:keep"), "rifle", id("test:zero"), "rifle", id("test:black"), "rifle",
                id("banned:gun"), "rifle", id("test:auto"), "rifle", id("test:rpg"), "rpg"), config::blacklisted);
        check(pool.size() == 2 && pool.get(id("test:keep")) == 7 && pool.containsKey(id("test:auto")),
                "blacklist/namespace/zero precedence and automatic category exclusion");
        check(weapon.selectAvailable(Map.of(), config::blacklisted).isEmpty(), "empty/unloaded gun index safe");
        JsonObject disabled = config.gunConfig();
        disabled.addProperty("auto_add_new_guns", false);
        check(HumanGunnerConfig.parse(disabled).selectAvailable(Map.of(id("test:auto"), "rifle"), s -> false).isEmpty(),
                "strict whitelist mode");
        disabled.add("hired_gun_additional_whitelist", JsonParser.parseString(
                "{\"test:hired_only\":4,\"test:disabled_hired\":0}"
        ));
        HumanGunnerConfig hiredConfig = HumanGunnerConfig.parse(disabled);
        check(hiredConfig.hiredGunAdditionalWhitelist().get(id("test:hired_only")) == 4,
                "hired-only gun whitelist parsed independently");
        check(!hiredConfig.selectAvailable(
                        Map.of(id("test:hired_only"), "rifle"), s -> false
                ).containsKey(id("test:hired_only")),
                "hired-only guns never enter the wild spawn pool");
        disabled.addProperty("human_gun_damage_multiplier", 2.5);
        check(HumanGunnerConfig.parse(disabled).humanGunDamageMultiplier() == 2.5, "gun multipliers above one allowed");

        JsonObject bad = JsonParser.parseString("""
                {"tiers":{"roamer":{"health_min":900,"health_max":5,"movement":{"base_speed":"NaN"},
                 "attack_damage":-30,"incoming_damage_multiplier":10000}},"spawning":{
                 "enabled":false,"admission_chance":2,"encounter_cooldown_ticks":-3,"density_divisor":0},
                 "recruitment":{"max_hired_by_tier":{"roamer":-3,"tier3":20000},
                 "allow_hired_pvp_damage":true}}
                """).getAsJsonObject();
        config = new UnifiedConfig(bad);
        check(config.tier("roamer").healthMin() == 5 && config.tier("roamer").healthMax() == 900, "normalize reversed health range");
        check(config.tier("roamer").baseMovementSpeed() == .105D, "NaN base movement speed fallback");
        check(config.tier("roamer").attackDamage() == 0, "negative attack clamp");
        check(config.tier("roamer").incomingDamage() == 10, "damage clamp");
        check(!config.spawn().enabled() && config.spawn().admissionChance() == 1, "spawn disable and clamp");
        check(config.spawn().cooldownTicks() == 0 && config.spawn().densityDivisor() == .1, "spawn safety bounds");
        check(config.recruitmentLimit(0) == 0 && config.recruitmentLimit(3) == 10000,
                "recruitment limits clamp safely");
        check(config.allowHiredPvpDamage(), "hired PvP damage opt-in parsed");
        for (int duration : new int[]{0, 1, 40, 2400, 72000}) {
            EncounterCooldown clock = new EncounterCooldown(duration);
            check(clock.join(100, new Object(), 1), "custom clock start");
            check(!clock.cooling(100L + duration), "custom clock exact expiry");
            if (duration > 0) check(clock.cooling(99L + duration), "custom clock before expiry");
        }
        TierConfigFilesTest.main(new String[0]);
        checkEquipmentImportAndCounts(defaults);
        System.out.println("UnifiedConfigTest: " + assertions + " checks passed");
    }

    private static void checkEquipmentImportAndCounts(JsonObject defaults) {
        JsonObject root = defaults.deepCopy();
        check(EquipmentIntegrationDefaults.seed(root, mod -> true, id -> true), "first import writes available items");
        check(!EquipmentIntegrationDefaults.seed(root, mod -> true, id -> true), "import receipt prevents repeated injection");
        JsonObject roamer = root.getAsJsonObject("equipment_loadouts").getAsJsonObject("roamer");
        roamer.add("mainhand", new JsonArray());
        roamer.add("shield_pool", new JsonArray());
        check(!EquipmentIntegrationDefaults.seed(root, mod -> true, id -> true)
                        && roamer.getAsJsonArray("mainhand").isEmpty()
                        && roamer.getAsJsonArray("shield_pool").isEmpty(), "deleted imported gear stays deleted");
        JsonObject unavailable = defaults.deepCopy();
        check(!EquipmentIntegrationDefaults.seed(unavailable, mod -> false, id -> true), "absent mods are never imported");
        check(!EquipmentIntegrationDefaults.seed(unavailable, mod -> true, id -> false), "unready registry never finalizes import");
        check(SpawnWeaponCountPolicy.roll(2, .45, .20, .44, 0) == 2
                        && SpawnWeaponCountPolicy.roll(3, .45, .20, .45, 0) == 1
                        && SpawnWeaponCountPolicy.roll(3, .45, .20, .44, .20) == 2
                        && SpawnWeaponCountPolicy.roll(3, .45, .20, .44, .19) == 3,
                "conditional weapon rolls enforce rank caps and strict probability boundaries");
        int[] counts = new int[4];
        for (int second = 0; second < 100; second++)
            for (int third = 0; third < 100; third++)
                counts[SpawnWeaponCountPolicy.roll(3, .45, .20, second / 100.0, third / 100.0)]++;
        check(counts[1] == 5500 && counts[2] == 3600 && counts[3] == 900, "default melee allocation is 55/36/9 percent");
        JsonObject gunsDisabled = defaults.deepCopy();
        gunsDisabled.getAsJsonObject("tacz").addProperty("natural_spawn_firearms_enabled", false);
        check(!new UnifiedConfig(gunsDisabled).naturalFirearmsEnabled(), "natural firearm switch is independent of integration");

    }

    private static ResourceLocation id(String id) { return ResourceLocation.tryParse(id); }
    private static void checkEquipmentLoadouts(UnifiedConfig defaults) {
        for (String rank : List.of("roamer", "tier1", "tier2", "tier3")) {
            var equipment = defaults.equipmentLoadout(rank);
            check(!equipment.mainhand.entries().isEmpty() && !equipment.armorSets.entries().isEmpty(),
                    "all four ranks have native config pools: " + rank);
        }
        check(defaults.equipmentLoadout("tier3").rangedChance == 1
                        && defaults.equipmentLoadout("tier3").rangedInBackpack
                        && defaults.equipmentLoadout("tier3").rules.eliteEnchantments(),
                "elite default ranged reserve and enchantment preset");
        JsonObject custom = JsonParser.parseString("""
                {"equipment_loadouts":{"tier3":{
                  "mainhand":[{"item":"spartanweaponry:diamond_scythe","weight":17,
                    "enchantments":{"minecraft:unbreaking":3}}],
                  "ranged_mainhand":[],"armor_sets":[],"inventory":[],
                  "automatic_spartan_weapons":false,"automatic_spartan_shields":false,
                  "allow_tacz_firearms":false,"ranged_chance":0.3,
                  "guaranteed_shields_min":0,"guaranteed_shields_max":0}}}
                """).getAsJsonObject();
        var equipment = new UnifiedConfig(custom).equipmentLoadout("tier3");
        check(equipment.mainhand.entries().size() == 1
                        && equipment.mainhand.entries().get(0).id().toString().equals("spartanweaponry:diamond_scythe")
                        && equipment.mainhand.entries().get(0).weight() == 17,
                "custom tier3 pool replaces defaults, arbitrary total weight");
        check(equipment.armorSets.entries().isEmpty() && equipment.rangedMainhand.entries().isEmpty()
                        && equipment.inventory.entries().isEmpty(), "intentional empty pools remain empty");
        check(!EquipmentIntegrationDefaults.seed(custom, mod -> true, item -> true) && !equipment.firearms
                        && equipment.rangedChance == .3 && equipment.shieldMin == 0 && equipment.shieldMax == 0,
                "custom gear controls and shield disabling are parsed");
        check(equipment.mainhand.entries().get(0).enchantments().get(id("minecraft:unbreaking")) == 3,
                "explicit enchantment levels are parsed without registry/world access");
        check(defaults.equipmentLoadout("roamer").rangedChance == .2
                        && defaults.equipmentLoadout("tier1").rangedChance == .4
                        && defaults.equipmentLoadout("tier2").rangedChance == .7,
                "existing nonelite ranged probabilities retained");
    }
    private static void check(boolean value, String name) { assertions++; if (!value) throw new AssertionError(name); }
}
