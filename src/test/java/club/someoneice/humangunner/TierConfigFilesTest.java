package club.someoneice.humangunner;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Filesystem and projection regressions; can be run as a standalone JavaExec main. */
public final class TierConfigFilesTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        checkProjection();
        checkOldestMigration();
        checkSingleMigrationAndOwnership();
        checkLegacyModulesAndInvalidSources();
        checkInvalidOldSingle();
        checkBrokenNewFilesAndPayments();
        checkBackupFailure();
        checkTomlMigration();
        checkCreationCollision();
        System.out.println("TierConfigFilesTest: " + assertions + " checks passed");
    }

    private static void checkProjection() {
        JsonObject root = UnifiedConfig.defaults();
        TierConfigProjection.put(root, "tiers.tier1.ammunition", json("{\"arrows\":{\"min\":3,\"max\":7},\"magazines\":{\"min\":1,\"max\":2}}"));
        TierConfigProjection.put(root, "equipment_loadouts.tier1.mainhand", new JsonArray());
        TierConfigProjection.put(root, "tacz.tier_gun_type_weights.tier1", new JsonObject());
        TierConfigProjection.put(root, "tiers.tier1._custom_note", new JsonPrimitive("keep"));
        TierConfigProjection.put(root, "tiers.tier1.extension", json("{\"amount\":42}"));
        TierConfigProjection.put(root, "tier_ai.tier1", json("{\"enabled\":false,\"retreat_health_ratio\":0.27,\"_custom_note\":\"tier AI\"}"));
        JsonObject global = TierConfigProjection.global(root);
        check(TierConfigProjection.at(global, "tiers.tier1") == null, "global has no per-rank attributes");
        check(TierConfigProjection.at(global, "equipment_loadouts.tier1") == null, "global has no rank equipment");
        check(TierConfigProjection.at(global, "tacz.tier1_gun_chance") == null, "global has no rank firearm chance");
        check(global.getAsJsonObject("ai").size() == 1 && global.getAsJsonObject("ai").has("enabled"), "global AI has only enabled");
        for (String rank : TierConfigProjection.RANKS) TierConfigProjection.applyTier(global, rank, TierConfigProjection.tier(root, rank));
        check(TierConfigProjection.at(global, "tiers.tier1.ammunition").equals(TierConfigProjection.at(root, "tiers.tier1.ammunition")), "ammunition ranges round trip");
        check(TierConfigProjection.at(global, "equipment_loadouts.tier1.mainhand").getAsJsonArray().isEmpty(), "custom empty equipment pool preserved");
        check(TierConfigProjection.at(global, "tacz.tier_gun_type_weights.tier1").getAsJsonObject().isEmpty(), "custom empty firearm weights preserved");
        check(TierConfigProjection.at(global, "tiers.tier1.extension.amount").getAsInt() == 42, "unknown rank fields preserved");
        check(TierConfigProjection.at(global, "tiers.tier1._custom_note").getAsString().equals("keep"), "custom tier guidance preserved");
        check(!TierConfigProjection.at(global, "tier_ai.tier1").getAsJsonObject().has("enabled"), "rank cannot override global AI toggle");
        check(TierConfigProjection.at(global, "tier_ai.tier1.retreat_health_ratio").getAsDouble() == 0.27, "explicit rank AI preserved");
        for (String rank : TierConfigProjection.RANKS) {
            check(TierConfigProjection.at(global, "recruitment.payment_by_tier." + rank).equals(TierConfigProjection.at(root, "recruitment.payment_by_tier." + rank)), "rank payment mapping " + rank);
            check(TierConfigProjection.at(global, "spawning.progression.first_spawn_day_by_tier." + rank).equals(TierConfigProjection.at(root, "spawning.progression.first_spawn_day_by_tier." + rank)), "first spawn day mapping " + rank);
        }
        JsonObject sparseWeights = json("{\"tacz\":{\"gun_type_weights\":{\"rifle\":17}},\"recruitment\":{\"payment\":{\"count\":8}}}");
        TierConfigProjection.fill(sparseWeights, TierConfigProjection.tier(root, "tier1"), "");
        check(sparseWeights.getAsJsonObject("tacz").getAsJsonObject("gun_type_weights").size() == 1, "deleted firearm weights are not restored");
        check(!sparseWeights.getAsJsonObject("recruitment").getAsJsonObject("payment").has("item"), "invalid partial payment not silently repaired");
        sparseWeights.getAsJsonObject("tacz").addProperty("extension_option", 23);
        TierConfigProjection.applyTier(global, "tier1", sparseWeights);
        check(TierConfigProjection.tier(global, "tier1").getAsJsonObject("tacz").get("extension_option").getAsInt() == 23,
                "unknown grouped rank option round trips through internal metadata");
    }

    private static void checkOldestMigration() throws Exception {
        Path directory = Files.createTempDirectory("human-oldest-layout-");
        try {
            String oldGuns = "{\"guns\":{\"custom:rifle\":37},\"gun_blacklist\":[\"test:black\"],\"include_unlisted_guns\":false}";
            String oldAi = "{\"enabled\":false,\"recovery_damage_tolerance\":18,\"_custom_note\":\"old AI\"}";
            Files.writeString(directory.resolve("humangunner.json"), oldGuns);
            Files.writeString(directory.resolve("humangunner_ai.json"), oldAi);
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "tacz.gun_whitelist.custom:rifle").getAsInt() == 37, "old gun whitelist alias migrated");
            check(new UnifiedConfig(loaded).blacklisted("test:black"), "disk migration retains blacklist");
            check(!TierConfigProjection.at(loaded, "ai.enabled").getAsBoolean(), "old AI global toggle migrated");
            for (String rank : TierConfigProjection.RANKS) {
                check(TierConfigProjection.at(loaded, "tier_ai." + rank + ".recovery_damage_tolerance_ratio").getAsDouble() == 0.3, "old common AI distributed " + rank);
                check(TierConfigProjection.at(loaded, "tier_ai." + rank + "._custom_note").getAsString().equals("old AI"), "AI notes distributed " + rank);
            }
            checkFiveFiles(directory);
            check(Files.readString(directory.resolve("humangunner.json")).equals(oldGuns), "old gun input untouched");
            check(Files.readString(directory.resolve("humangunner_ai.json.pre-tier-layout.bak")).equals(oldAi), "old AI exact backup");
        } finally { cleanup(directory); }
    }

    private static void checkSingleMigrationAndOwnership() throws Exception {
        Path directory = Files.createTempDirectory("human-single-layout-");
        try {
            JsonObject source = json("""
                    {"schema_version":1,"tiers":{"tier1":{"gun_spread_degrees":1.25,
                     "_说明_自定义":"保留这段说明","extension_setting":42}},
                     "equipment_loadouts":{"tier1":{"mainhand":[],"armor_sets":[]}},
                     "tacz":{"enabled":false,"gun_whitelist":{"custom:gun":37},"tier_gun_type_weights":{"tier1":{}}},
                     "recruitment":{"max_hired_by_tier":{"tier3":8},"payment_by_tier":{"tier1":{"item":"create:brass_ingot","count":37},"tier2":null}},
                     "spawning":{"admission_chance":0.321,"_说明_中文":"自定义说明"},"ai":{"recovery_health_ratio":0.71}}
                    """);
            String bytes = source.toString();
            Path single = directory.resolve("hostile_humans_unified.json"), modules = directory.resolve(SplitConfigFiles.DIRECTORY);
            Files.writeString(single, bytes);
            UnifiedConfig migrated = new UnifiedConfig(SplitConfigFiles.load(directory));
            check(migrated.tier("tier1").gunSpreadDegrees("rifle") == 1.25, "supported legacy accuracy alias wins before defaults");
            check(migrated.recruitmentLimit(3) == 8 && migrated.recruitmentCost(1).count() == 37, "single custom limit and currency migrate");
            check(migrated.recruitmentCost(2) == null && migrated.recruitmentCost(0).count() == 8, "invalid explicit payment isolated by tier");
            JsonObject tier = read(modules.resolve("tier1.json"));
            check(tier.get("extension_setting").getAsInt() == 42 && tier.get("_说明_自定义").getAsString().equals("保留这段说明"), "unknown fields and custom descriptions on disk");
            check(tier.getAsJsonObject("equipment_loadout").getAsJsonArray("mainhand").isEmpty(), "empty equipment pool on disk survives migration");
            check(tier.getAsJsonObject("tacz").getAsJsonObject("gun_type_weights").isEmpty(), "empty gun weights on disk survive migration");
            check(Files.readString(single).equals(bytes) && Files.readString(single.resolveSibling(single.getFileName() + ".pre-tier-layout.bak")).equals(bytes), "single source and exact backup intact");
            checkFiveFiles(directory);
            JsonObject global = read(modules.resolve("global.json"));
            check(global.getAsJsonObject("spawning").get("_说明_中文").getAsString().equals("自定义说明"), "user guidance remains intact");
            JsonObject defaultTacz = UnifiedConfig.defaults().getAsJsonObject("tacz");
            for (String key : List.of("_说明_枪械名单_中文", "_description_gun_lists_en"))
                check(global.getAsJsonObject("tacz").get(key).equals(defaultTacz.get(key)), "bilingual firearm guidance exact string " + key);
            global.getAsJsonObject("spawning").remove("progression");
            global.getAsJsonObject("spawning").addProperty("admission_chance", 0.123);
            global.getAsJsonObject("tacz").addProperty("enabled", true);
            Files.writeString(modules.resolve("global.json"), global.toString());
            tier.getAsJsonObject("spawning").addProperty("first_spawn_day", 7);
            tier.getAsJsonObject("recruitment").addProperty("max_hired", 9);
            Files.writeString(modules.resolve("tier1.json"), tier.toString());
            Files.writeString(single, "broken obsolete source");
            UnifiedConfig current = new UnifiedConfig(SplitConfigFiles.load(directory));
            check(current.taczEnabled() && current.recruitmentLimit(1) == 9, "new global and rank files override old source");
            check(current.spawn().admissionChance() == 0.123 && current.spawn().progression().tier1FirstDay() == 7, "global option defaults and tier day remain independently authoritative");
            String stable = Files.readString(modules.resolve("global.json"));
            SplitConfigFiles.load(directory);
            check(Files.readString(modules.resolve("global.json")).equals(stable), "existing incomplete new file never rewritten");
            check(Files.readString(single.resolveSibling(single.getFileName() + ".pre-tier-layout.bak")).equals(bytes), "backup written only once");
        } finally { cleanup(directory); }
    }

    private static void checkLegacyModulesAndInvalidSources() throws Exception {
        Path directory = Files.createTempDirectory("human-module-layout-");
        try {
            Path modules = directory.resolve(SplitConfigFiles.DIRECTORY);
            Files.createDirectories(modules);
            Files.writeString(directory.resolve("hostile_humans_unified.json"), "{\"schema_version\":1,\"spawning\":{\"admission_chance\":0.321},\"recruitment\":{\"max_hired_by_tier\":{\"tier1\":99}}}");
            String recruitment = "{\"schema_version\":1,\"_custom_note\":\"keep module note\",\"recruitment\":{\"max_hired_by_tier\":{\"tier1\":9},\"payment_by_tier\":{\"tier1\":{\"count\":37}}}}";
            Files.writeString(modules.resolve("recruitment.json"), recruitment);
            Files.writeString(modules.resolve("spawning.json"), "broken spawning JSON");
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "recruitment.max_hired_by_tier.tier1").getAsInt() == 9, "existing old module wins over old single file");
            check(!TierConfigProjection.at(loaded, "recruitment.payment_by_tier.tier1").getAsJsonObject().has("item"), "partial legacy price remains invalid");
            check(TierConfigProjection.at(loaded, "spawning.admission_chance").getAsDouble() == 0.321, "broken old module falls back to single values in memory");
            for (String file : SplitConfigFiles.FILES) check(!Files.exists(modules.resolve(file)), "invalid old custom source prevents replacement generation " + file);
            check(Files.readString(modules.resolve("spawning.json")).equals("broken spawning JSON"), "invalid old module bytes untouched");
            Files.writeString(modules.resolve("spawning.json"), "{\"schema_version\":1,\"spawning\":{\"admission_chance\":0.123}}");
            loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "spawning.admission_chance").getAsDouble() == 0.123, "repaired old module precedence");
            checkFiveFilesAlongsideLegacy(directory);
            check(Files.readString(modules.resolve("recruitment.json")).equals(recruitment), "valid old modules read only");
            check(TierConfigProjection.at(loaded, "_legacy_module_metadata.recruitment._custom_note").getAsString().equals("keep module note"), "module-level unknown notes retained in global metadata");
            Files.delete(modules.resolve("tier2.json"));
            Files.writeString(modules.resolve("recruitment.json"), "{\"schema_version\":99,\"recruitment\":{}}");
            loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "recruitment.payment_by_tier.tier2").isJsonNull(), "missing rank with invalid recruitment source fails closed");
            check(!Files.exists(modules.resolve("tier2.json")), "unsupported legacy version cannot generate dependent rank");
            check(TierConfigProjection.at(loaded, "recruitment.max_hired_by_tier.tier1").getAsInt() == 9, "existing rank unaffected by invalid legacy recruitment");
        } finally { cleanup(directory); }
    }

    private static void checkBrokenNewFilesAndPayments() throws Exception {
        Path directory = Files.createTempDirectory("human-new-invalid-");
        try {
            Files.writeString(directory.resolve("hostile_humans_unified.json"), "{\"schema_version\":1}");
            SplitConfigFiles.load(directory);
            Path modules = directory.resolve(SplitConfigFiles.DIRECTORY), file = modules.resolve("tier1.json");
            for (String invalid : List.of("broken tier JSON", "{\"schema_version\":99}", "{\"schema_version\":\"2\"}")) {
                Files.writeString(file, invalid);
                UnifiedConfig loaded = new UnifiedConfig(SplitConfigFiles.load(directory));
                check(loaded.recruitmentCost(1) == null, "invalid new rank disables that rank payment");
                check(loaded.recruitmentCost(2).count() == 72, "invalid new rank leaves other rank payment intact");
                check(Files.readString(file).equals(invalid), "invalid or unsupported new file never overwritten");
            }
            Files.writeString(file, "{\"schema_version\":2,\"recruitment\":null}");
            check(new UnifiedConfig(SplitConfigFiles.load(directory)).recruitmentCost(1) == null, "invalid new recruitment object fails closed");
            Files.writeString(file, "{\"schema_version\":2,\"recruitment\":{\"payment\":{\"item\":\"create:brass_ingot\",\"count\":37}}}");
            check(new UnifiedConfig(SplitConfigFiles.load(directory)).recruitmentCost(1).count() == 37, "valid modded currency preserved");
            JsonObject global = read(modules.resolve("global.json"));
            TierConfigProjection.put(global, "tiers.tier1.attributes.health_min", new JsonPrimitive(999));
            TierConfigProjection.put(global, "recruitment.max_hired_by_tier.tier1", new JsonPrimitive(999));
            Files.writeString(modules.resolve("global.json"), global.toString());
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "tiers.tier1.attributes.health_min").getAsInt() != 999, "global cannot override rank attributes");
            check(TierConfigProjection.at(loaded, "recruitment.max_hired_by_tier.tier1").getAsInt() != 999, "global cannot override rank recruitment");
        } finally { cleanup(directory); }
    }

    private static void checkInvalidOldSingle() throws Exception {
        Path directory = Files.createTempDirectory("human-invalid-single-");
        try {
            Path single = directory.resolve("hostile_humans_unified.json");
            for (String invalid : List.of("malformed legacy JSON", "{\"schema_version\":99,\"tacz\":{\"enabled\":false}}", "[]")) {
                Files.writeString(single, invalid);
                JsonObject loaded = SplitConfigFiles.load(directory);
                check(new UnifiedConfig(loaded).spawn().admissionChance() == 0.08, "invalid single falls back in memory");
                for (int tier = 0; tier < 4; tier++)
                    check(new UnifiedConfig(loaded).recruitmentCost(tier) == null, "invalid single recruitment fails closed");
                for (String file : SplitConfigFiles.FILES)
                    check(!Files.exists(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(file)), "invalid single cannot generate replacement " + file);
                check(Files.readString(single).equals(invalid), "invalid single bytes untouched");
            }
        } finally { cleanup(directory); }
    }

    private static void checkBackupFailure() throws Exception {
        Path directory = Files.createTempDirectory("human-backup-failure-");
        try {
            Path single = directory.resolve("hostile_humans_unified.json");
            String source = "{\"schema_version\":1,\"spawning\":{\"admission_chance\":0.23}}";
            Files.writeString(single, source);
            Files.createDirectory(single.resolveSibling(single.getFileName() + ".pre-tier-layout.bak"));
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "spawning.admission_chance").getAsDouble() == 0.23, "backup failure retains in-memory custom settings");
            for (String file : SplitConfigFiles.FILES) check(!Files.exists(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(file)), "backup failure postpones generation " + file);
            check(Files.readString(single).equals(source), "backup failure does not modify source");
        } finally { cleanup(directory); }
    }

    private static void checkTomlMigration() throws Exception {
        Path directory = Files.createTempDirectory("human-toml-layout-");
        try {
            String toml = """
                    ["Hostile Humans Settings"]
                    disabled_structures = "cottage,tower"
                    max_targeting = 7
                    no_waystones = true
                    battle_event = "Random"
                    flee_hp = 0.42
                    run_away_middle_fight_chance = 0.67
                    mid_battle_buff_instead_of_run_chance = 0.23
                    """;
            Files.writeString(directory.resolve("hostile_humans.toml"), toml);
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "native_gameplay.max_targeting").getAsInt() == 7, "TOML global value survives implicit migrate defaults");
            check(TierConfigProjection.at(loaded, "native_gameplay.no_waystones").getAsBoolean(), "TOML waystone toggle migrated");
            for (String rank : TierConfigProjection.RANKS)
                check(TierConfigProjection.at(loaded, "tier_ai." + rank + ".flee_hp").getAsDouble() == 0.42, "TOML AI distributed " + rank);
            check(Files.readString(directory.resolve("hostile_humans.toml.pre-tier.bak")).equals(toml), "TOML exact backup");
            Path modules = directory.resolve(SplitConfigFiles.DIRECTORY);
            for (String file : SplitConfigFiles.FILES) Files.delete(modules.resolve(file));
            Files.writeString(directory.resolve("hostile_humans_unified.json"), "{\"schema_version\":1,\"native_gameplay\":{\"max_targeting\":11},\"ai\":{\"flee_hp\":0.19}}");
            loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "native_gameplay.max_targeting").getAsInt() == 11, "explicit JSON overrides TOML");
            check(TierConfigProjection.at(loaded, "native_gameplay.no_waystones").getAsBoolean(), "missing JSON option migrates lower-priority TOML");
            check(TierConfigProjection.at(loaded, "tier_ai.tier1.flee_hp").getAsDouble() == 0.19, "explicit JSON AI overrides TOML");
            for (String file : SplitConfigFiles.FILES) Files.delete(modules.resolve(file));
            Files.writeString(directory.resolve("hostile_humans.toml"), "[broken table");
            SplitConfigFiles.load(directory);
            for (String file : SplitConfigFiles.FILES) check(!Files.exists(modules.resolve(file)), "invalid TOML postpones dependent generation " + file);
            check(Files.readString(directory.resolve("hostile_humans.toml")).equals("[broken table"), "invalid TOML never rewritten");
        } finally { cleanup(directory); }
    }

    private static void checkCreationCollision() throws Exception {
        Path directory = Files.createTempDirectory("human-config-collision-");
        try {
            Path file = directory.resolve("tier1.json");
            String winner = "{\"schema_version\":2,\"recruitment\":{\"payment\":null},\"_custom_note\":\"concurrent writer\"}";
            Files.writeString(file, winner);
            boolean rejected = false;
            try { SplitConfigFiles.writeNew(file, json("{\"schema_version\":2}")); }
            catch (FileAlreadyExistsException expected) { rejected = true; }
            check(rejected && Files.readString(file).equals(winner), "create collision cannot replace concurrent winner");
            try (var paths = Files.list(directory)) { check(paths.count() == 1, "collision removes temporary file"); }
        } finally { cleanup(directory); }
    }

    private static void checkFiveFiles(Path directory) throws Exception {
        checkFiveFilesAlongsideLegacy(directory);
        try (var paths = Files.list(directory.resolve(SplitConfigFiles.DIRECTORY))) {
            check(paths.filter(path -> path.toString().endsWith(".json")).count() == 5, "new installation creates exactly five JSON files");
        }
    }
    private static void checkFiveFilesAlongsideLegacy(Path directory) throws Exception {
        for (String file : SplitConfigFiles.FILES) {
            JsonObject data = read(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(file));
            check(data.get("schema_version").getAsInt() == 2, "v2 version " + file);
            check(data.has("_说明_中文") && data.has("_description_en"), "bilingual file guidance " + file);
        }
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static JsonObject read(Path path) throws Exception { return json(Files.readString(path)); }
    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); assertions++; }
    private static void cleanup(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
