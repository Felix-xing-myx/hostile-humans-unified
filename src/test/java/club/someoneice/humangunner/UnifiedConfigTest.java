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
        check(config.tier("roamer").gunSpreadDegrees() == 2.5D
                        && config.tier("tier1").gunSpreadDegrees() == 2.0D
                        && config.tier("tier2").gunSpreadDegrees() == 1.5D
                        && config.tier("tier3").gunSpreadDegrees() == 1.0D,
                "per-tier firearm spread defaults");
        for (String type : List.of("sniper", "rifle", "mg", "smg", "pistol", "shotgun")) {
            double previous = Double.POSITIVE_INFINITY;
            for (String tier : List.of("roamer", "tier1", "tier2", "tier3")) {
                double spread = GunSpreadPolicy.degrees(config.tier(tier).gunSpreadDegrees(), type);
                if (previous != Double.POSITIVE_INFINITY) {
                    check(previous - spread == 0.5D, type + " has a half-degree tier step");
                }
                previous = spread;
            }
        }
        check(GunSpreadPolicy.degrees(config.tier("tier3").gunSpreadDegrees(), "sniper") == 0.5D
                        && GunSpreadPolicy.degrees(config.tier("tier3").gunSpreadDegrees(), "rifle") == 1.0D
                        && GunSpreadPolicy.degrees(config.tier("tier3").gunSpreadDegrees(), "mg") == 1.5D
                        && GunSpreadPolicy.degrees(config.tier("tier3").gunSpreadDegrees(), "pistol") == 2.0D
                        && GunSpreadPolicy.degrees(config.tier("tier3").gunSpreadDegrees(), "shotgun") == 2.5D,
                "each weapon family reaches its intended top-tier accuracy floor");
        double previousBow = Double.POSITIVE_INFINITY;
        double previousCrossbow = Double.POSITIVE_INFINITY;
        for (String tier : List.of("roamer", "tier1", "tier2", "tier3")) {
            double bow = RangedAccuracy.projectileSpreadDegrees(
                    config.tier(tier).projectileSpreadDegrees(), false);
            double crossbow = RangedAccuracy.projectileSpreadDegrees(
                    config.tier(tier).projectileSpreadDegrees(), true);
            check(Math.abs(bow - crossbow - 0.5D) < 1.0E-9D,
                    "crossbow is half a degree more accurate than bow and trident");
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
                {"tiers":{"roamer":{"gun_spread_degrees":0.75},
                           "tier1":{"gun_spread_degrees":1.25}}}
                """).getAsJsonObject();
        UnifiedConfig adjustableAccuracy = new UnifiedConfig(accuracyOverride);
        check(adjustableAccuracy.tier("roamer").gunSpreadDegrees() == 0.75D
                        && adjustableAccuracy.tier("tier1").gunSpreadDegrees() == 1.25D
                        && adjustableAccuracy.tier("tier2").gunSpreadDegrees() == 1.5D,
                "per-tier firearm spread is independently adjustable with other tiers retaining defaults");

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
        check(config.tier("tier2").normalSpeed() == .42, "migrate all tier speeds");
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
                {"tiers":{"roamer":{"health_min":900,"health_max":5,"normal_movement_speed":"NaN",
                 "attack_damage":-30,"incoming_damage_multiplier":10000}},"spawning":{
                 "enabled":false,"admission_chance":2,"encounter_cooldown_ticks":-3,"density_divisor":0},
                 "recruitment":{"max_hired_by_tier":{"roamer":-3,"tier3":20000},
                 "allow_hired_pvp_damage":true}}
                """).getAsJsonObject();
        config = new UnifiedConfig(bad);
        check(config.tier("roamer").healthMin() == 5 && config.tier("roamer").healthMax() == 900, "normalize reversed health range");
        check(config.tier("roamer").normalSpeed() == .1, "NaN fallback");
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
