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
            check(Math.abs(bow - crossbow - 0.5D) < 1.0E-9D,
                    "the default crossbow is half a degree more accurate than bow and trident");
            if (previousBow != Double.POSITIVE_INFINITY) {
                check(Math.abs(previousBow - bow - 0.3D) < 1.0E-9D
                                && Math.abs(previousCrossbow - crossbow - 0.3D) < 1.0E-9D,
                        "bow, crossbow and trident improve by 0.3 degrees per tier");
            }
            previousBow = bow;
            previousCrossbow = crossbow;
        }
        check(Math.abs(previousBow - 1.5D) < 1.0E-9D
                        && Math.abs(previousCrossbow - 1.0D) < 1.0E-9D,
                "top-tier bow and trident reach 1.5 degrees, crossbow reaches 1 degree");
        check(config.tier("tier3").projectileSpreadDegrees("trident") == 1.5D,
                "trident has its own spread setting with the existing default");
        check(config.spawn().admissionChance() == .08, "legacy spawn chance");
        check(config.tier("tier3").spawnMultiplier() == .04, "legacy rare tier");
        check(config.recruitmentLimit(0) == 12 && config.recruitmentLimit(1) == 10
                && config.recruitmentLimit(2) == 6 && config.recruitmentLimit(3) == 3,
                "legacy recruitment limits retained as defaults");
        check(!config.allowHiredPvpDamage(), "hired PvP damage defaults off");
        check(Math.abs(config.damage("human_bow_damage_multiplier", 0) / 1.44D - 0.7D) < 1.0E-9D
                        && Math.abs(config.damage("human_trident_damage_multiplier", 0) / 1.2D - 0.7D) < 1.0E-9D
                        && Math.abs(config.damage("trident_stab_damage_multiplier", 0) / 0.6D - 0.7D) < 1.0E-9D,
                "human arrows, thrown tridents and trident stabs each lose thirty percent damage");
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
        java.nio.file.Path newFile = directory.resolve("hostile_humans_unified.json");
        try {
            String original = guns.toString();
            java.nio.file.Files.writeString(legacyFile, original);
            UnifiedConfig first = UnifiedConfig.load(directory);
            check(first.blacklisted("test:black"), "disk migration blacklist");
            check(java.nio.file.Files.readString(legacyFile).equals(original), "legacy file never modified");
            JsonObject saved = JsonParser.parseString(java.nio.file.Files.readString(newFile)).getAsJsonObject();
            check(new ArrayList<>(saved.keySet()).get(saved.size()-1).equals("tacz"), "generated config TaCZ last");
            saved.getAsJsonObject("tacz").addProperty("enabled", false);
            java.nio.file.Files.writeString(newFile, saved.toString());
            java.nio.file.Files.writeString(legacyFile, "{}");
            check(!UnifiedConfig.load(directory).taczEnabled(), "existing unified config wins over legacy");
            String bytes = java.nio.file.Files.readString(newFile);
            UnifiedConfig.load(directory);
            check(java.nio.file.Files.readString(newFile).equals(bytes), "existing settings never rewritten");
        } finally {
            java.nio.file.Files.deleteIfExists(newFile);
            java.nio.file.Files.deleteIfExists(legacyFile);
            java.nio.file.Files.deleteIfExists(directory);
        }
        System.out.println("UnifiedConfigTest: " + assertions + " checks passed");
    }
    private static ResourceLocation id(String id) { return ResourceLocation.tryParse(id); }
    private static void check(boolean value, String name) { assertions++; if (!value) throw new AssertionError(name); }
}
