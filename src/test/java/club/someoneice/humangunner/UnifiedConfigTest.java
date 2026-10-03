package club.someoneice.humangunner;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class UnifiedConfigTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        check(new ArrayList<>(defaults.keySet()).get(defaults.size()-1).equals("tacz"), "TaCZ section last");
        UnifiedConfig config = new UnifiedConfig(new JsonObject());
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
        JsonObject legacyAccuracy = JsonParser.parseString("""
                {"tiers":{"tier1":{"health_min":55,"normal_movement_speed":0.42,
                  "gun_spread_degrees":1.25,"projectile_spread_degrees":3.0}}}
                """).getAsJsonObject();
        UnifiedConfig legacyConfig = new UnifiedConfig(legacyAccuracy);
        check(legacyConfig.tier("tier1").healthMin() == 55
                        && legacyConfig.tier("tier1").baseMovementSpeed() == .105D
                        && legacyConfig.tier("tier1").gunSpreadDegrees("rifle") == 1.25D
                        && legacyConfig.tier("tier1").gunSpreadDegrees("sniper") == 0.75D
                        && legacyConfig.tier("tier1").gunSpreadDegrees("shotgun") == 2.75D
                        && legacyConfig.tier("tier1").projectileSpreadDegrees("bow") == 3.0D
                        && legacyConfig.tier("tier1").projectileSpreadDegrees("crossbow") == 2.5D,
                "legacy speed setting is ignored while legacy spread settings keep their prior behavior");

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

        JsonObject guns = JsonParser.parseString("""
                {"globe":0.9,"guns":{"test:keep":7,"test:zero":0,"banned:gun":100,"test:black":2},
                 "include_unlisted_guns":true,"excluded_gun_namespaces":["banned"],
                 "gun_blacklist":["test:black"],"human_melee_damage_multiplier":2.75}
                """).getAsJsonObject();
        JsonObject ai = JsonParser.parseString("""
                {"normal_movement_speed":0.42,"recovery_damage_tolerance":12}
                """).getAsJsonObject();
        JsonObject migrated = UnifiedConfig.migrate(guns.deepCopy(), ai);
        config = new UnifiedConfig(migrated);
        check(config.tier("tier2").baseMovementSpeed() == .105D
                        && !config.ai().has("normal_movement_speed"),
                "legacy speed migration is discarded in favor of new base-speed defaults");
        check(config.ai().get("recovery_damage_tolerance_ratio").getAsDouble() == .2, "legacy absolute tolerance");
        check(config.damage("human_melee_damage_multiplier", 1) == 2.75, "legacy damage");
        check(config.blacklisted("test:black"), "blacklist migration");
        HumanGunnerConfig weapon = HumanGunnerConfig.parse(config.gunConfig());
        check(weapon.tier2GunChance() == .9, "legacy globe alias");
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
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("hostile-humans-config-test-");
        java.nio.file.Path legacyFile = directory.resolve("humangunner.json");
        java.nio.file.Path oldUnified = directory.resolve("hostile_humans_unified.json");
        java.nio.file.Path modules = directory.resolve(SplitConfigFiles.DIRECTORY);
        java.nio.file.Path gunFile = modules.resolve("tacz.json");
        java.nio.file.Path spawnFile = modules.resolve("spawning.json");
        try {
            String original = guns.toString();
            java.nio.file.Files.writeString(legacyFile, original);
            UnifiedConfig first = UnifiedConfig.load(directory);
            check(first.blacklisted("test:black"), "disk migration blacklist");
            check(java.nio.file.Files.readString(legacyFile).equals(original), "legacy file never modified");
            for (String file : List.of("tiers.json", "spawning.json", "equipment.json", "combat.json", "recruitment.json", "tacz.json", "compatibility.json")) {
                JsonObject module = readModule(modules.resolve(file));
                check(module.has("_说明_中文") && module.has("_description_en"), "module bilingual guidance: " + file);
                for (var section : module.entrySet()) {
                    if (defaults.has(section.getKey()) && section.getValue().isJsonObject()
                            && defaults.get(section.getKey()).isJsonObject()) {
                        checkDescriptions(section.getValue().getAsJsonObject(),
                                defaults.getAsJsonObject(section.getKey()), file + ":" + section.getKey());
                    }
                }
            }
            JsonObject saved = readModule(gunFile);
            check(saved.getAsJsonObject("tacz").has("_说明_中文"), "nested descriptions retained");
            check(saved.getAsJsonObject("tacz").get("_说明_枪械名单_中文")
                            .equals(defaults.getAsJsonObject("tacz").get("_说明_枪械名单_中文"))
                            && saved.getAsJsonObject("tacz").get("_description_gun_lists_en")
                            .equals(defaults.getAsJsonObject("tacz").get("_description_gun_lists_en")),
                    "bilingual firearm-list examples retain exact string contents");
            JsonObject tierModule = readModule(modules.resolve("tiers.json"));
            check(tierModule.getAsJsonObject("tiers").equals(defaults.getAsJsonObject("tiers")),
                    "full tier settings and nested descriptions migrate without changes");
            saved.getAsJsonObject("tacz").addProperty("enabled", false);
            java.nio.file.Files.writeString(gunFile, saved.toString());
            java.nio.file.Files.writeString(legacyFile, "{}");
            check(!UnifiedConfig.load(directory).taczEnabled(), "split config wins over oldest legacy");
            String bytes = java.nio.file.Files.readString(gunFile);
            UnifiedConfig.load(directory);
            check(java.nio.file.Files.readString(gunFile).equals(bytes), "complete module never rewritten");
            java.nio.file.Path recruitmentFile = modules.resolve("recruitment.json");
            JsonObject oldRecruitment = readModule(recruitmentFile);
            JsonObject recruitmentSettings = oldRecruitment.getAsJsonObject("recruitment");
            recruitmentSettings.remove("payment_by_tier");
            recruitmentSettings.getAsJsonObject("max_hired_by_tier").addProperty("tier3", 9);
            recruitmentSettings.addProperty("allow_hired_pvp_damage", true);
            recruitmentSettings.addProperty("_custom_note", "keep my note");
            String oldRecruitmentBytes = oldRecruitment.toString();
            java.nio.file.Files.writeString(recruitmentFile, oldRecruitmentBytes);
            UnifiedConfig upgradedRecruitment = UnifiedConfig.load(directory);
            JsonObject storedRecruitment = readModule(recruitmentFile).getAsJsonObject("recruitment");
            check(storedRecruitment.getAsJsonObject("payment_by_tier").equals(
                    defaults.getAsJsonObject("recruitment").getAsJsonObject("payment_by_tier")),
                    "old recruitment file persists payment defaults with bilingual guidance");
            check(upgradedRecruitment.recruitmentLimit(3) == 9 && upgradedRecruitment.allowHiredPvpDamage()
                    && storedRecruitment.get("_custom_note").getAsString().equals("keep my note"),
                    "recruitment upgrade preserves custom values and notes");
            check(java.nio.file.Files.readString(modules.resolve("recruitment.json.pre-upgrade.bak"))
                    .equals(oldRecruitmentBytes), "automatic upgrade backs up the original bytes");
            JsonObject partialPayments = new JsonObject();
            partialPayments.add("tier1", JsonParser.parseString(
                    "{\"item\":\"create:brass_ingot\",\"count\":37,\"_custom_note\":\"keep\"}"));
            partialPayments.add("tier2", JsonNull.INSTANCE);
            oldRecruitment.getAsJsonObject("recruitment").add("payment_by_tier", partialPayments);
            java.nio.file.Files.writeString(recruitmentFile, oldRecruitment.toString());
            UnifiedConfig partialRecruitment = UnifiedConfig.load(directory);
            check(partialRecruitment.recruitmentCost(1).equals(
                    new UnifiedConfig.RecruitmentCost("create:brass_ingot", 37)),
                    "partial payment upgrade preserves custom item ID and count");
            check(partialRecruitment.recruitmentCost(0).count() == 8
                    && partialRecruitment.recruitmentCost(3).count() == 216,
                    "partial payment upgrade adds missing tiers");
            check(readModule(recruitmentFile).getAsJsonObject("recruitment")
                    .getAsJsonObject("payment_by_tier").get("tier2").isJsonNull()
                    && partialRecruitment.recruitmentCost(2) == null,
                    "malformed explicit payment preserved and fails closed");
            String paymentBytes = java.nio.file.Files.readString(recruitmentFile);
            UnifiedConfig.load(directory);
            check(java.nio.file.Files.readString(recruitmentFile).equals(paymentBytes),
                    "recruitment upgrade is idempotent");
            JsonObject olderSettings = readModule(spawnFile);
            olderSettings.getAsJsonObject("spawning").remove("progression");
            olderSettings.getAsJsonObject("spawning").addProperty("admission_chance", 0.123);
            java.nio.file.Files.writeString(spawnFile, olderSettings.toString());
            UnifiedConfig.load(directory);
            JsonObject upgraded = readModule(spawnFile);
            check(upgraded.getAsJsonObject("spawning").getAsJsonObject("progression")
                            .getAsJsonObject("first_spawn_day_by_tier").get("tier3").getAsInt() == 20,
                    "older config receives progression defaults");
            check(UnifiedConfig.load(directory).spawn().admissionChance() == 0.123,
                    "new options preserve existing spawning settings");
            JsonObject partial = upgraded.getAsJsonObject("spawning").getAsJsonObject("progression");
            partial.addProperty("enabled", false);
            partial.addProperty("safe_days", 4);
            partial.getAsJsonObject("first_spawn_day_by_tier").addProperty("roamer", 7);
            partial.getAsJsonObject("first_spawn_day_by_tier").remove("tier2");
            java.nio.file.Files.writeString(spawnFile, upgraded.toString());
            NaturalSpawnProgression parsed = UnifiedConfig.load(directory).spawn().progression();
            check(!parsed.enabled() && parsed.safeDays() == 4 && parsed.roamerFirstDay() == 7
                            && parsed.tier2FirstDay() == 10,
                    "partial progression defaults added without overwriting custom settings");
            String stable = java.nio.file.Files.readString(spawnFile);
            UnifiedConfig.load(directory);
            check(java.nio.file.Files.readString(spawnFile).equals(stable), "option upgrade is idempotent");

            JsonObject legacyUnified = JsonParser.parseString("""
                    {"schema_version":1,"tiers":{"tier1":{"gun_spread_degrees":1.25,
                     "_说明_自定义":"保留这段说明","extension_setting":42}},
                     "tacz":{"enabled":true,"gun_whitelist":{"custom:gun":37}},
                     "recruitment":{"max_hired_by_tier":{"tier3":8}},
                     "spawning":{"admission_chance":0.321,"_说明_中文":"自定义说明"}}
                    """).getAsJsonObject();
            String oldBytes = legacyUnified.toString();
            java.nio.file.Files.writeString(oldUnified, oldBytes);
            java.nio.file.Files.delete(modules.resolve("tiers.json"));
            java.nio.file.Files.delete(modules.resolve("recruitment.json"));
            check(UnifiedConfig.load(directory).recruitmentLimit(3) == 8, "missing module migrates old custom values");
            check(UnifiedConfig.load(directory).tier("tier1").gunSpreadDegrees("rifle") == 1.25D,
                    "migration preserves supported legacy accuracy alias precedence");
            JsonObject migratedTier = readModule(modules.resolve("tiers.json"))
                    .getAsJsonObject("tiers").getAsJsonObject("tier1");
            check(migratedTier.get("_说明_自定义").getAsString().equals("保留这段说明")
                            && migratedTier.get("extension_setting").getAsInt() == 42,
                    "custom descriptions and unknown extension fields survive migration");
            check(!UnifiedConfig.load(directory).taczEnabled(), "existing module takes precedence over old unified config");
            check(java.nio.file.Files.readString(oldUnified).equals(oldBytes), "old unified source remains byte intact");
            check(java.nio.file.Files.readString(directory.resolve("hostile_humans_unified.json.pre-split.bak")).equals(oldBytes),
                    "migration backup preserves original bytes");
            java.nio.file.Files.writeString(spawnFile, "broken JSON");
            check(UnifiedConfig.load(directory).spawn().admissionChance() == 0.321,
                    "broken module falls back only to its old section");
            check(!UnifiedConfig.load(directory).taczEnabled(), "broken spawning does not reset valid gun module");
            check(java.nio.file.Files.readString(spawnFile).equals("broken JSON"), "broken module is never overwritten");
            java.nio.file.Files.delete(oldUnified);
            check(UnifiedConfig.load(directory).spawn().admissionChance() == 0.08, "broken module without legacy uses defaults");
            java.nio.file.Files.writeString(gunFile, "{\"schema_version\":99,\"tacz\":{\"enabled\":false}}");
            check(UnifiedConfig.load(directory).taczEnabled(), "unsupported module schema falls back safely");
            java.nio.file.Files.delete(spawnFile);
            java.nio.file.Files.writeString(oldUnified, "malformed legacy JSON");
            check(UnifiedConfig.load(directory).spawn().admissionChance() == 0.08,
                    "invalid migration source uses defaults in memory");
            check(!java.nio.file.Files.exists(spawnFile), "invalid source cannot generate a replacement module");
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    java.nio.file.Files.deleteIfExists(path);
            }
        }
        checkGeneralSchemaUpgrade(defaults);
        checkDamageGuidanceUpgrade(defaults);
        checkRoamerArmorUpgrade(defaults);
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
        JsonObject guidance = defaults.deepCopy();
        JsonObject notes = guidance.getAsJsonObject("equipment_loadouts");
        notes.addProperty("_description_priority_en", "Order: configured base gear -> permitted automatic mod equipment -> shield/ranged reserves -> natural equipment growth. automatic_spartan_weapons/shields default true and allow original Spartan replacement/addition. For strict custom pools, disable the relevant switches and specify mod item IDs directly. allow_tacz_firearms enables/disables this rank's automatic firearm generation; gun chances/real gun IDs still belong in tacz.json. Do not use tacz:modern_kinetic_gun in these pools: it does not select an actual gun model. Immersive Armors use requires_mod armor_sets rows, without a separate armor replacement pass. Enabled equipment_growth may weaken early gear after generation; disable growth for fixed quality.");
        notes.addProperty("_说明_优先级与联动_中文", "整合包作者的自定义说明");
        check(ConfigSchemaUpgrade.fill(guidance, defaults, "")
                        && notes.get("_description_priority_en").equals(defaults.getAsJsonObject("equipment_loadouts").get("_description_priority_en"))
                        && notes.get("_说明_优先级与联动_中文").getAsString().equals("整合包作者的自定义说明"),
                "known stock replacement instructions refresh while custom equipment notes survive");
    }

    private static void checkRoamerArmorUpgrade(JsonObject defaults) {
        JsonObject loadouts = defaults.getAsJsonObject("equipment_loadouts");
        JsonArray current = loadouts.getAsJsonObject("roamer").getAsJsonArray("armor_sets");
        check(current.size() == 3, "roamer default armor has no high-end optional sets");
        Map<String, Integer> weights = new HashMap<>();
        for (JsonElement entry : current) {
            JsonObject set = entry.getAsJsonObject();
            weights.put(set.get("head").getAsString(), set.get("weight").getAsInt());
            for (String slot : List.of("head", "chest", "legs", "feet")) {
                String id = set.get(slot).getAsString();
                check(id.startsWith("minecraft:leather_") || id.startsWith("minecraft:chainmail_")
                        || id.startsWith("minecraft:iron_"), "roamer armor stays at iron tier or below");
            }
        }
        check(weights.equals(Map.of("minecraft:leather_helmet", 6,
                "minecraft:chainmail_helmet", 3, "minecraft:iron_helmet", 1)),
                "roamer default weights favor weaker armor than tier one");
        JsonArray old = new JsonArray();
        for (String material : List.of("iron", "diamond", "chainmail", "slime", "prismarine", "steampunk")) {
            boolean optional = old.size() >= 3;
            String prefix = (optional ? "immersive_armors:" : "minecraft:") + material;
            JsonObject set = new JsonObject();
            set.addProperty("weight", material.equals("iron") ? 2 : 1);
            if (optional) set.addProperty("requires_mod", "immersive_armors");
            set.addProperty("head", prefix + "_helmet");
            set.addProperty("chest", prefix + "_chestplate");
            set.addProperty("legs", prefix + "_leggings");
            set.addProperty("feet", prefix + "_boots");
            old.add(set);
        }
        JsonObject stock = defaults.deepCopy();
        JsonObject roamer = stock.getAsJsonObject("equipment_loadouts").getAsJsonObject("roamer");
        roamer.add("armor_sets", old.deepCopy());
        JsonElement tierOne = stock.getAsJsonObject("equipment_loadouts").get("tier1").deepCopy();
        check(ConfigSchemaUpgrade.fill(stock, defaults, "") && roamer.get("armor_sets").equals(current),
                "exact previous stock roamer armor migrates to the weaker pool");
        check(stock.getAsJsonObject("equipment_loadouts").get("tier1").equals(tierOne),
                "roamer upgrade never changes tier-one gear");
        check(!ConfigSchemaUpgrade.fill(stock, defaults, ""), "roamer armor upgrade is idempotent");
        for (int mode = 0; mode < 3; mode++) {
            JsonArray custom = old.deepCopy();
            if (mode == 0) custom.get(0).getAsJsonObject().addProperty("weight", 9);
            if (mode == 1) custom.remove(1);
            if (mode == 2) custom = new JsonArray();
            roamer.add("armor_sets", custom.deepCopy());
            ConfigSchemaUpgrade.fill(stock, defaults, "");
            check(roamer.get("armor_sets").equals(custom), "custom weights, entries and empty armor pools are preserved");
        }
    }

    private static void checkDamageGuidanceUpgrade(JsonObject defaults) {
        JsonObject custom = defaults.deepCopy();
        JsonObject damage = custom.getAsJsonObject("damage");
        damage.addProperty("_说明_中文", "全局伤害倍率，用于调整各类人类攻击、弹道速度伤害、跳跃攻击，以及人类近战对已驯服女仆造成的伤害。tamed_maid_melee_damage_multiplier 只削减人类对女仆的近战伤害，不控制女仆对人类的伤害。");
        damage.addProperty("_description_en", "My custom damage notes");
        damage.addProperty("human_melee_damage_multiplier", 1.25);
        JsonObject tier = custom.getAsJsonObject("tiers").getAsJsonObject("tier1")
                .getAsJsonObject("damage_multipliers");
        tier.addProperty("_说明_中文", "本阶人类近战、弓箭、三叉戟伤害倍率及受到伤害倍率。");
        tier.addProperty("melee_damage_multiplier", 0.6);
        JsonObject tacz = custom.getAsJsonObject("tacz");
        tacz.addProperty("_description_gun_damage_en",
                "Base firearm damage for humans and pillagers, plus per-tier firearm damage multipliers.");
        tacz.addProperty("human_gun_damage_multiplier", 0.4);
        tacz.getAsJsonObject("tier_damage_multipliers").addProperty("tier1", 1.5);
        check(ConfigSchemaUpgrade.fill(custom, defaults, ""), "known obsolete damage guidance refreshed");
        check(damage.get("_说明_中文").equals(defaults.getAsJsonObject("damage").get("_说明_中文"))
                        && tier.get("_说明_中文").equals(defaults.getAsJsonObject("tiers")
                        .getAsJsonObject("tier1").getAsJsonObject("damage_multipliers").get("_说明_中文"))
                        && tacz.get("_description_gun_damage_en")
                        .equals(defaults.getAsJsonObject("tacz").get("_description_gun_damage_en")),
                "stock global/tier/firearm comments describe their multiplying relationship");
        check(damage.get("_description_en").getAsString().equals("My custom damage notes"),
                "user-written guidance is not replaced");
        UnifiedConfig settings = new UnifiedConfig(custom);
        check(Math.abs(settings.damage("human_melee_damage_multiplier", 1)
                        * settings.tier("tier1").meleeDamage() - 0.75) < 1e-9,
                "guidance upgrade does not alter global or tier melee values");
        check(Math.abs(settings.gunSetting("human_gun_damage_multiplier", 1)
                        * settings.tierGunDamage("tier1") - 0.6) < 1e-9,
                "guidance upgrade does not alter global or tier firearm values");
        check(!ConfigSchemaUpgrade.fill(custom, defaults, ""), "damage guidance upgrade is idempotent");
    }

    private static void checkGeneralSchemaUpgrade(JsonObject defaults) throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("human-schema-upgrade-test-");
        var modules = directory.resolve(SplitConfigFiles.DIRECTORY);
        java.nio.file.Files.createDirectories(modules);
        Map<String, List<String>> files = new LinkedHashMap<>();
        files.put("tiers.json", List.of("tiers"));
        files.put("spawning.json", List.of("spawning"));
        files.put("equipment.json", List.of("equipment_loadouts", "equipment_growth"));
        files.put("combat.json", List.of("damage", "ai"));
        files.put("recruitment.json", List.of("recruitment"));
        files.put("tacz.json", List.of("tacz"));
        files.put("compatibility.json", List.of("better_combat"));
        JsonObject old = JsonParser.parseString("""
                {"tiers":{"tier1":{"health_min":66,"health_max":73,"gun_spread_degrees":1.7,
                 "projectile_spread_degrees":2.5,"melee_cooldown_min":30,"melee_cooldown_max":34}},
                 "spawning":{"admission_chance":0.123},"ai":{"_custom_note":"keep"},"damage":{},
                 "recruitment":{"payment_by_tier":{"roamer":{"item":"createdelightcore:gold_coin","count":19}}},
                 "tacz":{"guns":{},"include_unlisted_guns":false,"unlisted_gun_weight":13,"globe":0.2},
                 "better_combat":{},"equipment_growth":{},"equipment_loadouts":{}}
                """).getAsJsonObject();
        try {
            for (var entry : files.entrySet()) {
                JsonObject module = new JsonObject();
                module.addProperty("schema_version", 1);
                for (String section : entry.getValue()) module.add(section, old.get(section).deepCopy());
                java.nio.file.Files.writeString(modules.resolve(entry.getKey()), module.toString());
            }
            UnifiedConfig upgraded = UnifiedConfig.load(directory);
            check(upgraded.tier("tier1").healthMin() == 66 && upgraded.tier("tier1").healthMax() == 73
                    && upgraded.tier("tier1").meleeCooldownMin() == 30
                    && upgraded.tier("tier1").meleeCooldownMax() == 34, "flat legacy attributes and cooldown survive full upgrade");
            check(Math.abs(upgraded.tier("tier1").gunSpreadDegrees("smg") - 2.7) < 1e-9
                    && upgraded.tier("tier1").projectileSpreadDegrees("crossbow") == 2.0,
                    "legacy spread migrated before inserting modern defaults");
            check(upgraded.spawn().admissionChance() == 0.123
                    && upgraded.recruitmentCost(0).itemId().equals("createdelightcore:gold_coin")
                    && upgraded.recruitmentCost(0).count() == 19,
                    "disk loading respects the user's actual mod currency and existing values");
            HumanGunnerConfig guns = HumanGunnerConfig.parse(upgraded.gunConfig());
            check(guns.gunWhitelist().isEmpty(), "explicit empty legacy whitelist stays empty through disk upgrade and runtime merge");
            for (var entry : files.entrySet()) {
                var file = modules.resolve(entry.getKey());
                JsonObject module = readModule(file);
                for (String section : entry.getValue())
                    checkSchemaKeys(module.getAsJsonObject(section), defaults.getAsJsonObject(section), section);
                String stable = java.nio.file.Files.readString(file);
                UnifiedConfig.load(directory);
                check(java.nio.file.Files.readString(file).equals(stable), "all-module upgrade idempotent: " + entry.getKey());
                check(java.nio.file.Files.exists(file.resolveSibling(file.getFileName() + ".pre-upgrade.bak")),
                        "each upgraded module has an original backup: " + entry.getKey());
            }
            JsonObject futureDefaults = defaults.deepCopy();
            futureDefaults.getAsJsonObject("ai").add("future_feature", JsonParser.parseString(
                    "{\"_说明_中文\":\"新选项\",\"_description_en\":\"New option\",\"enabled\":true,\"amount\":17}"));
            JsonObject earlier = old.deepCopy();
            ConfigSchemaUpgrade.aliases(earlier, futureDefaults);
            check(ConfigSchemaUpgrade.fill(earlier, futureDefaults, "")
                    && earlier.getAsJsonObject("ai").getAsJsonObject("future_feature").get("amount").getAsInt() == 17,
                    "a new nested feature and comments require no upgrade whitelist changes");
            check(!ConfigSchemaUpgrade.fill(earlier, futureDefaults, ""), "future schema upgrade also idempotent");
            UnifiedConfig unavailable = UnifiedConfig.load(null);
            for (int tier = 0; tier < 4; tier++)
                check(unavailable.recruitmentCost(tier) == null, "total config load failure cannot silently charge emeralds");
            var recruitment = modules.resolve("recruitment.json");
            java.nio.file.Files.writeString(recruitment, "broken recruitment JSON");
            UnifiedConfig invalid = UnifiedConfig.load(directory);
            for (int tier = 0; tier < 4; tier++)
                check(invalid.recruitmentCost(tier) == null, "broken recruitment file cannot silently charge emeralds");
            check(java.nio.file.Files.readString(recruitment).equals("broken recruitment JSON"),
                    "broken input left intact for administrator repair");
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private static void checkSchemaKeys(JsonObject actual, JsonObject expected, String path) {
        if (ConfigSchemaUpgrade.userMap(path)) return;
        for (var entry : expected.entrySet()) {
            String child = path + "." + entry.getKey();
            check(actual.has(entry.getKey()), "complete new schema key on disk: " + child);
            if (entry.getValue().isJsonObject())
                checkSchemaKeys(actual.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject(), child);
        }
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
    private static void checkDescriptions(JsonObject actual, JsonObject expected, String path) {
        for (var entry : expected.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) {
                check(actual.has(key) && actual.get(key).equals(entry.getValue()),
                        "description preserved exactly: " + path + "." + key);
            } else if (entry.getValue().isJsonObject() && actual.has(key)
                    && actual.get(key).isJsonObject()) {
                checkDescriptions(actual.getAsJsonObject(key), entry.getValue().getAsJsonObject(), path + "." + key);
            }
        }
    }
    private static JsonObject readModule(java.nio.file.Path path) throws java.io.IOException {
        return JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
    }
    private static void check(boolean value, String name) { assertions++; if (!value) throw new AssertionError(name); }
}
