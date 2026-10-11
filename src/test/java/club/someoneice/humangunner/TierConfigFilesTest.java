package club.someoneice.humangunner;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Filesystem and projection regressions; can be run as a standalone JavaExec main. */
public final class TierConfigFilesTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        checkNewBehaviorSections();
        checkIndependentGunDamage();
        checkImmersiveArmorImport();
        checkScgunsArmorImport();
        checkMasterIntegrationSwitch();
        checkDefaultIntegrationRetry();
        checkProjection();
        checkRetiredControls();
        checkReadableDefaults();
        checkOwnership();
        checkBrokenNewFilesAndPayments();
        checkCurrentLayoutOnly();
        checkCreationCollision();
        System.out.println("TierConfigFilesTest: " + assertions + " checks passed");
    }

    private static void checkNewBehaviorSections() throws Exception {
        Path directory = Files.createTempDirectory("human-behavior-config-");
        try {
            Path global = directory.resolve(SplitConfigFiles.DIRECTORY).resolve("global.json");
            Files.createDirectories(global.getParent());
            Files.writeString(global, "{\"schema_version\":2,\"damage\":{\"jump_attack_damage_multiplier\":2.4},\"custom\":17}");
            JsonObject defaults = UnifiedConfig.defaults();
            UnifiedConfig loaded = new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (r, d) -> false));
            JsonObject saved = read(global);
            check(saved.has("jump_attack") && saved.has("first_aid"), "new behavior sections are editable on disk");
            check(loaded.jumpAttack().damageMultiplier() == 2.4 && saved.get("custom").getAsInt() == 17,
                    "existing custom multiplier and extensions survive new section creation");
            check(!saved.has("growth") && !saved.has("recruitment"), "unrelated missing settings remain memory-only");
            String stable = Files.readString(global);
            SplitConfigFiles.load(directory, defaults, (r, d) -> false);
            check(stable.equals(Files.readString(global)), "new section creation is idempotent");
            try (var files = Files.list(global.getParent())) {
                check(files.allMatch(path -> path.getFileName().toString().endsWith(".json")),
                        "no migration or inherited files created");
            }
        } finally { cleanup(directory); }
    }

    private static void checkIndependentGunDamage() throws Exception {
        Path directory = Files.createTempDirectory("human-independent-guns-");
        try {
            JsonObject defaults = UnifiedConfig.defaults();
            SplitConfigFiles.load(directory, defaults, (r, d) -> false);
            Path path = directory.resolve(SplitConfigFiles.DIRECTORY).resolve("tier1.json");
            JsonObject rank = read(path);
            rank.remove("scguns");
            TierConfigProjection.put(rank, "tacz.damage_multiplier", new JsonPrimitive(2.5));
            Files.writeString(path, rank.toString());
            UnifiedConfig loaded = new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (r, d) -> false));
            check(loaded.tierGunDamage("tier1") == 2.5 && loaded.tierScgunsDamage("tier1") == 1,
                    "existing TaCZ factor does not seed Scorched Guns factor");
            rank = read(path);
            check(TierConfigProjection.at(rank, "scguns.damage_multiplier").getAsDouble() == 1,
                    "new Scorched Guns factor is exposed in existing rank file");
            TierConfigProjection.put(rank, "scguns.damage_multiplier", new JsonPrimitive(.8));
            TierConfigProjection.put(rank, "scguns.enabled", new JsonPrimitive(false));
            Files.writeString(path, rank.toString());
            loaded = new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (r, d) -> false));
            check(loaded.tierGunDamage("tier1") == 2.5 && loaded.tierScgunsDamage("tier1") == .8,
                    "independent backend factors survive reload");
            check(TierConfigProjection.at(read(path), "scguns.enabled") == null,
                    "Scorched Guns master switch remains global only");
        } finally { cleanup(directory); }
    }

    private static void checkOwnership() throws Exception {
        Path directory = Files.createTempDirectory("human-config-ownership-");
        try {
            JsonObject defaults = UnifiedConfig.defaults();
            SplitConfigFiles.load(directory, defaults, (r, d) -> false);
            Path modules = directory.resolve(SplitConfigFiles.DIRECTORY);
            JsonObject global = read(modules.resolve("global.json"));
            TierConfigProjection.put(global, "recruitment.max_hired_by_tier.tier1", new JsonPrimitive(123));
            TierConfigProjection.put(global, "damage.arrow_damage_multiplier", new JsonPrimitive(99));
            TierConfigProjection.put(global, "ai.retreat_health_ratio", new JsonPrimitive(.8));
            TierConfigProjection.put(global, "ammunition.arrows.min", new JsonPrimitive(999));
            TierConfigProjection.put(global, "equipment_growth.start_day", new JsonPrimitive(999));
            TierConfigProjection.put(global, "tacz.human_gun_damage_multiplier", new JsonPrimitive(9));
            Files.writeString(modules.resolve("global.json"), global.toString());
            JsonObject rank = read(modules.resolve("tier1.json"));
            TierConfigProjection.put(rank, "recruitment.max_hired", new JsonPrimitive(999));
            TierConfigProjection.put(rank, "ai.enabled", new JsonPrimitive(false));
            TierConfigProjection.put(rank, "spawning.enabled", new JsonPrimitive(false));
            TierConfigProjection.put(rank, "damage_multipliers.jump_attack_damage_multiplier", new JsonPrimitive(99));
            TierConfigProjection.put(rank, "ammunition.consume_ammunition", new JsonPrimitive(false));
            TierConfigProjection.put(rank, "tacz.enabled", new JsonPrimitive(false));
            TierConfigProjection.put(rank, "tacz.damage_multiplier", new JsonPrimitive(2.5));
            TierConfigProjection.put(rank, "damage_multipliers.arrow_damage_multiplier", new JsonPrimitive(3));
            Files.writeString(modules.resolve("tier1.json"), rank.toString());
            UnifiedConfig loaded = new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (r, d) -> false));
            check(loaded.recruitmentLimit(1) == 123 && loaded.tier("tier1").bowDamage() == 3
                    && loaded.tierGunDamage("tier1") == 2.5, "global hiring and independent rank output");
            rank = read(modules.resolve("tier1.json"));
            global = read(modules.resolve("global.json"));
            for (String path : List.of("recruitment", "ai.enabled", "ammunition.consume_ammunition", "tacz.enabled", "spawning.enabled", "damage_multipliers.jump_attack_damage_multiplier"))
                check(TierConfigProjection.at(rank, path) == null, "global-only control removed from rank: " + path);
            for (String path : List.of("damage.arrow_damage_multiplier", "ai.retreat_health_ratio", "tacz.human_gun_damage_multiplier", "ammunition.arrows", "equipment_growth.start_day"))
                check(TierConfigProjection.at(global, path) == null, "rank-only control removed from global: " + path);
            String stableGlobal = Files.readString(modules.resolve("global.json"));
            String stableRank = Files.readString(modules.resolve("tier1.json"));
            SplitConfigFiles.load(directory, defaults, (r, d) -> false);
            check(stableGlobal.equals(Files.readString(modules.resolve("global.json")))
                    && stableRank.equals(Files.readString(modules.resolve("tier1.json"))), "second load is stable");
            Files.writeString(modules.resolve("global.json"), "broken global JSON");
            check(new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (r, d) -> false)).recruitmentCost(1) == null,
                    "broken global payment fails closed");
        } finally { cleanup(directory); }
    }

    private static void checkRetiredControls() throws Exception {
        var dir = Files.createTempDirectory("human-retired-controls-");
        try {
            var defaults = UnifiedConfig.defaults();
            SplitConfigFiles.load(dir, defaults, (rank, data) -> false);
            var globalPath = dir.resolve(SplitConfigFiles.DIRECTORY).resolve("global.json");
            var global = JsonParser.parseString(Files.readString(globalPath)).getAsJsonObject();
            global.getAsJsonObject("native_gameplay").addProperty("no_waystones", true);
            global.getAsJsonObject("tacz").addProperty("gunner_spread_degrees", 7);
            global.getAsJsonObject("spawning").addProperty("roamer_legacy_roll", 200);
            global.getAsJsonObject("spawning").addProperty("_description_roamer_fix_en", "roamer_legacy_roll ignored");
            var damage = global.getAsJsonObject("damage");
            damage.addProperty("human_bow_damage_multiplier", 8);
            damage.addProperty("bow_velocity_damage_multiplier", 9);
            damage.addProperty("_description_bow_velocity_damage_multiplier_en", "retired");
            damage.addProperty("arrow_damage_multiplier", 72);
            damage.addProperty("custom_extension", 42);
            Files.writeString(globalPath, global.toString());
            var rankPath = dir.resolve(SplitConfigFiles.DIRECTORY).resolve("tier1.json");
            var rank = JsonParser.parseString(Files.readString(rankPath)).getAsJsonObject();
            rank.addProperty("normal_movement_speed", 0.42);
            rank.addProperty("gun_spread_degrees", 4);
            rank.getAsJsonObject("movement").addProperty("retreat_movement_speed", 0.8);
            rank.getAsJsonObject("ai").addProperty("_说明_旧原生行为_中文", "outdated heading");
            rank.getAsJsonObject("potions").add("bonus", json("{\"chance\":1}"));
            rank.getAsJsonObject("damage_multipliers").addProperty("bow_damage_multiplier", 9);
            rank.getAsJsonObject("damage_multipliers").addProperty("arrow_damage_multiplier", 2);
            Files.writeString(rankPath, rank.toString());
            var loaded = new UnifiedConfig(SplitConfigFiles.load(dir, defaults, (r, data) -> false));
            check(loaded.tier("tier1").bowDamage() == 2,
                    "rank damage is the sole outgoing source");
            global = JsonParser.parseString(Files.readString(globalPath)).getAsJsonObject();
            damage = global.getAsJsonObject("damage");
            check(!damage.has("arrow_damage_multiplier") && !damage.has("human_bow_damage_multiplier") && !damage.has("bow_velocity_damage_multiplier")
                    && !damage.has("_description_bow_velocity_damage_multiplier_en"), "retired global keys and comments removed on disk");
            check(damage.get("custom_extension").getAsInt() == 42, "unknown extension preserved");
            check(!global.getAsJsonObject("native_gameplay").has("no_waystones"), "inactive Waystones switch removed");
            check(!global.getAsJsonObject("tacz").has("gunner_spread_degrees")
                    && !global.getAsJsonObject("spawning").has("roamer_legacy_roll")
                    && !global.getAsJsonObject("spawning").has("_description_roamer_fix_en"), "unused global controls and explanation removed");
            rank = JsonParser.parseString(Files.readString(rankPath)).getAsJsonObject();
            check(!rank.getAsJsonObject("potions").has("bonus")
                    && !rank.getAsJsonObject("damage_multipliers").has("bow_damage_multiplier"), "retired rank controls removed on disk");
            check(!rank.has("normal_movement_speed") && !rank.has("gun_spread_degrees")
                    && !rank.getAsJsonObject("movement").has("retreat_movement_speed")
                    && !rank.getAsJsonObject("ai").has("_说明_旧原生行为_中文"), "ignored tier controls and obsolete heading removed");
            try (var files = Files.list(globalPath.getParent())) {
                check(files.count() == 5, "cleanup creates no inheritance, backup or migration files");
            }
        } finally { cleanup(dir); }
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
        check(global.getAsJsonObject("ai").keySet().stream().filter(key -> !key.startsWith("_")).toList()
                .equals(List.of("enabled")), "global AI has only enabled plus guidance");
        for (String rank : TierConfigProjection.RANKS) TierConfigProjection.applyTier(global, rank, TierConfigProjection.tier(root, rank));
        check(TierConfigProjection.at(global, "tiers.tier1.ammunition").equals(TierConfigProjection.at(root, "tiers.tier1.ammunition")), "ammunition ranges round trip");
        check(TierConfigProjection.at(global, "equipment_loadouts.tier1.mainhand").getAsJsonArray().size() == 0, "custom empty equipment pool preserved");
        check(TierConfigProjection.at(global, "tacz.tier_gun_type_weights.tier1").getAsJsonObject().size() == 0, "custom empty firearm weights preserved");
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
        JsonObject sparsePayment = json("{\"recruitment\":{\"payment_by_tier\":{\"tier1\":{\"count\":8}}}}");
        TierConfigProjection.fill(sparsePayment, TierConfigProjection.global(root), "");
        check(!TierConfigProjection.at(sparsePayment, "recruitment.payment_by_tier.tier1").getAsJsonObject().has("item"),
                "invalid partial global payment not silently repaired");
        sparseWeights.getAsJsonObject("tacz").addProperty("extension_option", 23);
        TierConfigProjection.applyTier(global, "tier1", sparseWeights);
        check(TierConfigProjection.tier(global, "tier1").getAsJsonObject("tacz").get("extension_option").getAsInt() == 23,
                "unknown grouped rank option round trips through internal metadata");
    }

    private static void checkReadableDefaults() throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        JsonObject global = TierConfigProjection.global(defaults);
        checkAdjacentHelp(global, "global");
        check(!global.has("tiers") && !global.has("equipment_loadouts"), "no orphan rank/loadout guidance in global");
        check(TierConfigProjection.at(global, "spawning.progression.first_spawn_day_by_tier") == null,
                "no orphan rank unlock guidance in global");
        check(global.has("growth") && !global.has("equipment_growth"), "unified growth lives in global");
        check(!global.getAsJsonObject("tacz").has("_description_gun_type_weights_en"), "type-weight guidance moves to ranks");
        JsonObject custom = defaults.deepCopy();
        custom.getAsJsonObject("tiers").addProperty("_description_custom_extension_en", "Keep my custom notes");
        check(TierConfigProjection.global(custom).getAsJsonObject("tiers").has("_description_custom_extension_en"),
                "unknown custom guidance is not discarded");
        TierConfigProjection.put(custom, "equipment_loadouts.tier1", JsonNull.INSTANCE);
        TierConfigProjection.put(custom, "tiers.tier1.damage_multipliers", JsonNull.INSTANCE);
        JsonObject invalid = TierConfigProjection.tier(custom, "tier1");
        check(invalid.get("equipment_loadout").isJsonNull() && !invalid.has("growth") && !invalid.has("equipment_growth")
                && invalid.get("damage_multipliers").isJsonNull(), "guidance does not replace explicit invalid values");
        Path directory = Files.createTempDirectory("human-readable-config-");
        try {
            JsonObject assembled = SplitConfigFiles.load(directory, defaults);
            UnifiedConfig baseline = new UnifiedConfig(defaults), projected = new UnifiedConfig(assembled);
            for (String rank : TierConfigProjection.RANKS) {
                JsonObject file = SplitConfigFiles.read(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(rank + ".json"), 2);
                checkAdjacentHelp(file, rank);
                check(!file.has("recruitment"), "hiring terms live only in global: " + rank);
                List<String> sections = new ArrayList<>(file.keySet());
                check(sections.get(0).equals("schema_version"), "schema first " + rank);
                check(sections.indexOf("attributes") < sections.indexOf("ai")
                        && sections.indexOf("ammunition") < sections.indexOf("ai")
                        && sections.indexOf("ai") < sections.indexOf("growth_profile")
                        && sections.indexOf("growth_profile") < sections.indexOf("equipment_loadout"), "consistent section order " + rank);
                for (String path : List.of("attributes.max_health", "movement.base_speed", "ai.retreat_health_ratio",
                        "equipment_loadout.ranged_chance", "ammunition.arrows.min",
                        "potions.recovery.count_min", "tacz.gun_chance", "spawning.first_spawn_day")) {
                    int dot = path.lastIndexOf('.');
                    JsonObject section = TierConfigProjection.at(file, path.substring(0, dot)).getAsJsonObject();
                    String field = path.substring(dot + 1);
                    List<String> keys = new ArrayList<>(section.keySet());
                    int index = keys.indexOf(field);
                    check(index >= 2 && keys.get(index - 2).equals("_说明_" + field + "_中文")
                            && keys.get(index - 1).equals("_description_" + field + "_en"), "adjacent bilingual help " + rank + "." + path);
                }
                check(baseline.tier(rank).equals(projected.tier(rank)), "rank behavior unchanged " + rank);
                check(baseline.combatAi(rank).equals(projected.combatAi(rank)), "AI behavior unchanged " + rank);
                check(baseline.ammoReserve(rank).equals(projected.ammoReserve(rank)), "ammunition unchanged " + rank);
                check(baseline.recruitmentCost(TierConfigProjection.RANKS.indexOf(rank)).equals(
                        projected.recruitmentCost(TierConfigProjection.RANKS.indexOf(rank))), "payment unchanged " + rank);
            }
        } finally { cleanup(directory); }
    }

    private static void checkAdjacentHelp(JsonObject object, String path) {
        List<String> keys = new ArrayList<>(object.keySet());
        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            if (key.equals("_说明_中文") || key.equals("_description_en")) continue;
            if (key.startsWith("_说明_") && key.endsWith("_中文")) {
                check(object.has(key.substring(4, key.length() - 3)), "no orphan help " + path + "." + key);
                continue;
            }
            if (key.startsWith("_description_") && key.endsWith("_en")) {
                check(object.has(key.substring(13, key.length() - 3)), "no orphan help " + path + "." + key);
                continue;
            }
            if (key.startsWith("_")) continue;
            String zh = "_说明_" + key + "_中文", en = "_description_" + key + "_en";
            if (object.has(zh) || object.has(en)) {
                int index = keys.indexOf(key);
                check(index >= 2 && keys.get(index - 2).equals(zh) && keys.get(index - 1).equals(en),
                        "help immediately precedes control " + path + "." + key);
            }
            if (entry.getValue().isJsonObject()) checkAdjacentHelp(entry.getValue().getAsJsonObject(), path + "." + key);
        }
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
                check(loaded.recruitmentCost(1).count() == 24, "invalid rank cannot change global payment");
                check(loaded.recruitmentCost(2).count() == 72, "invalid new rank leaves other rank payment intact");
                check(Files.readString(file).equals(invalid), "invalid or unsupported new file never overwritten");
            }
            Files.writeString(file, "{\"schema_version\":2,\"recruitment\":null}");
            check(new UnifiedConfig(SplitConfigFiles.load(directory)).recruitmentCost(1).count() == 24,
                    "rank recruitment cannot override global terms");
            JsonObject priceGlobal = read(modules.resolve("global.json"));
            TierConfigProjection.put(priceGlobal, "recruitment.payment_by_tier.tier1",
                    json("{\"item\":\"create:brass_ingot\",\"count\":37}"));
            Files.writeString(modules.resolve("global.json"), priceGlobal.toString());
            check(new UnifiedConfig(SplitConfigFiles.load(directory)).recruitmentCost(1).count() == 37, "valid global modded currency preserved");
            JsonObject global = read(modules.resolve("global.json"));
            TierConfigProjection.put(global, "tiers.tier1.attributes.max_health", new JsonPrimitive(999));
            TierConfigProjection.put(global, "recruitment.max_hired_by_tier.tier1", new JsonPrimitive(999));
            Files.writeString(modules.resolve("global.json"), global.toString());
            JsonObject loaded = SplitConfigFiles.load(directory);
            check(TierConfigProjection.at(loaded, "tiers.tier1.attributes.max_health").getAsInt() != 999, "global cannot override rank attributes");
            check(TierConfigProjection.at(loaded, "recruitment.max_hired_by_tier.tier1").getAsInt() == 999, "global owns rank hiring limits");
        } finally { cleanup(directory); }
    }

    private static void checkImmersiveArmorImport() throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        java.util.function.Predicate<String> onlyArmor = mod -> mod.equals("immersive_armors");
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject file = TierConfigProjection.tier(defaults, rank);
            JsonObject baseline = TierConfigProjection.tier(defaults, rank);
            JsonArray armor = file.getAsJsonObject("equipment_loadout").getAsJsonArray("armor_sets");
            for (var row : armor) check(!row.getAsJsonObject().has("requires_mod"), "fresh base armor excludes automatic mod rows " + rank);
            int baseSize = armor.size();
            check(!EquipmentIntegrationDefaults.seedRank(rank, file, baseline, onlyArmor, item -> false),
                    "unregistered armor does not finalize import " + rank);
            boolean imported = EquipmentIntegrationDefaults.seedRank(rank, file, baseline, onlyArmor, item -> true);
            if (rank.equals("roamer")) {
                check(!imported && armor.size() == baseSize, "roamer keeps original vanilla-only armor distribution");
                continue;
            }
            check(imported && armor.size() > baseSize
                    && file.getAsJsonObject("equipment_loadout").get("_imported_immersive_armors").getAsBoolean(),
                    "complete sets imported with receipt " + rank);
            for (int i = baseSize; i < armor.size(); i++) {
                var row = armor.get(i).getAsJsonObject();
                check(row.get("requires_mod").getAsString().equals("immersive_armors"), "import retains mod requirement");
                check(row.get("weight").getAsInt() == (rank.equals("tier3") ? 38 : 1), "original tier armor weight retained");
            }
            armor.remove(armor.size() - 1);
            JsonArray deleted = armor.deepCopy();
            check(!EquipmentIntegrationDefaults.seedRank(rank, file, baseline, onlyArmor, item -> true)
                    && armor.equals(deleted), "deleted imported armor does not reappear " + rank);
            JsonObject customFile = baseline.deepCopy();
            JsonObject customGear = customFile.getAsJsonObject("equipment_loadout");
            JsonArray customArmor = customGear.getAsJsonArray("armor_sets");
            JsonObject existing = armor.get(baseSize).getAsJsonObject().deepCopy();
            existing.addProperty("weight", 0);
            existing.addProperty("_custom_note", "keep my disabled set");
            customArmor.add(existing);
            int count = customArmor.size();
            check(EquipmentIntegrationDefaults.seedRank(rank, customFile, baseline, onlyArmor, item -> true)
                    && customArmor.contains(existing), "existing set weight zero and notes retained " + rank);
            int duplicates = 0;
            for (var row : customArmor)
                if (existing.get("head").equals(row.getAsJsonObject().get("head"))) duplicates++;
            check(duplicates == 1 && customArmor.size() >= count, "same armor tuple never duplicated " + rank);
            customFile = baseline.deepCopy();
            customFile.getAsJsonObject("equipment_loadout").add("armor_sets", new JsonArray());
            check(!EquipmentIntegrationDefaults.seedRank(rank, customFile, baseline, onlyArmor, item -> true), "empty armor remains empty " + rank);
            customFile = baseline.deepCopy();
            customFile.getAsJsonObject("equipment_loadout").addProperty("automatic_immersive_armors", false);
            check(!EquipmentIntegrationDefaults.seedRank(rank, customFile, baseline, onlyArmor, item -> true), "rank armor import can be disabled " + rank);
            customFile = baseline.deepCopy();
            check(!EquipmentIntegrationDefaults.seedRank(rank, customFile, baseline, onlyArmor, item -> !item.endsWith("_boots")),
                    "missing set piece cannot import partial armor " + rank);
        }
    }

    private static void checkScgunsArmorImport() {
        JsonObject defaults = UnifiedConfig.defaults();
        Set<String> allIds = new HashSet<>();
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject file = TierConfigProjection.tier(defaults, rank);
            JsonObject gear = file.getAsJsonObject("equipment_loadout");
            JsonArray before = gear.getAsJsonArray("armor_sets").deepCopy();
            check(!EquipmentIntegrationDefaults.seedRank(rank, file, file, "scguns"::equals, id -> false),
                    "Scguns unready registry cannot finalize " + rank);
            check(!gear.has("_imported_scguns_armor"), "unready Scguns receipt absent " + rank);
            check(EquipmentIntegrationDefaults.seedRank(rank, file, file, "scguns"::equals, id -> true),
                    "Scguns ordinary armor imported " + rank);
            JsonArray rows = gear.getAsJsonArray("armor_sets");
            for (var row : before) check(rows.contains(row), "custom/original armor row retained " + rank);
            for (var value : rows) {
                JsonObject row = value.getAsJsonObject();
                if (!row.has("requires_mod") || !row.get("requires_mod").getAsString().equals("scguns")) continue;
                for (String slot : List.of("head", "chest", "legs", "feet")) {
                    String id = row.get(slot).getAsString();
                    check(!id.contains("exo_suit") && !id.contains("module"), "only ordinary armor " + id);
                    allIds.add(id);
                }
            }
            rows.get(rows.size() - 1).getAsJsonObject().addProperty("weight", 0);
            JsonArray preserved = rows.deepCopy();
            check(!EquipmentIntegrationDefaults.seedRank(rank, file, file, "scguns"::equals, id -> true)
                    && rows.equals(preserved), "receipt preserves edits/weights " + rank);
            file = TierConfigProjection.tier(defaults, rank);
            file.getAsJsonObject("equipment_loadout").add("armor_sets", new JsonArray());
            check(!EquipmentIntegrationDefaults.seedRank(rank, file, file, "scguns"::equals, id -> true),
                    "Scguns empty armor pool remains empty " + rank);
        }
        check(allIds.size() == 31 && allIds.containsAll(Set.of("scguns:redcoat_hat", "scguns:ridgetop",
                "scguns:iron_mask", "scguns:brass_mask")), "all registered ordinary Scguns armor covered");
    }

    private static void checkMasterIntegrationSwitch() throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        Path directory = Files.createTempDirectory("human-spartan-master-");
        java.util.function.BiPredicate<String, JsonObject> ready = (rank, file) ->
                EquipmentIntegrationDefaults.seedRank(rank, file, TierConfigProjection.tier(defaults, rank),
                        mod -> true, item -> true);
        try {
            Path ranks = directory.resolve(SplitConfigFiles.DIRECTORY);
            Files.createDirectories(ranks);
            JsonObject global = TierConfigProjection.global(defaults);
            TierConfigProjection.put(global, "equipment_integration.auto_import_enabled", new JsonPrimitive(false));
            Files.writeString(ranks.resolve("global.json"), global.toString());
            // No rank files or legacy sources: the global switch must still block the fresh path.
            SplitConfigFiles.load(directory, defaults, ready);
            Map<String, byte[]> before = new HashMap<>();
            for (String name : SplitConfigFiles.FILES) before.put(name, Files.readAllBytes(ranks.resolve(name)));
            for (String rank : TierConfigProjection.RANKS) {
                JsonObject gear = read(ranks.resolve(rank + ".json")).getAsJsonObject("equipment_loadout");
                check(!gear.has("_imported_spartanweaponry") && !gear.has("_imported_spartanshields"),
                        "global false blocks even new rank imports " + rank);
            }
            SplitConfigFiles.load(directory, defaults, ready);
            for (String name : SplitConfigFiles.FILES)
                check(Arrays.equals(before.get(name), Files.readAllBytes(ranks.resolve(name))), "disabled reload leaves bytes intact " + name);
            try (var paths = Files.list(ranks)) { check(paths.count() == 5, "disabled imports create no backups or temporary files"); }
            for (String invalid : List.of("not json", "{\"schema_version\":999}")) {
                Files.writeString(ranks.resolve("global.json"), invalid);
                SplitConfigFiles.load(directory, defaults, ready);
                for (String rank : TierConfigProjection.RANKS)
                    check(Arrays.equals(before.get(rank + ".json"), Files.readAllBytes(ranks.resolve(rank + ".json"))),
                            "unreadable global cannot authorize rank modifications " + rank);
                check(Files.readString(ranks.resolve("global.json")).equals(invalid), "unreadable global remains untouched");
            }
            TierConfigProjection.put(global, "equipment_integration.auto_import_enabled", new JsonPrimitive(true));
            Files.writeString(ranks.resolve("global.json"), global.toString());
            JsonObject tier1 = read(ranks.resolve("tier1.json"));
            JsonArray custom = tier1.getAsJsonObject("equipment_loadout").getAsJsonArray("mainhand");
            JsonObject row = json("{\"item\":\"example:custom_blade\",\"weight\":42,\"count\":1}");
            custom.add(row);
            // Preconfigured import candidate with weight zero must remain disabled, never rewritten to one.
            JsonObject disabled = json("{\"item\":\"spartanweaponry:iron_longsword\",\"weight\":0}");
            custom.add(disabled);
            Files.writeString(ranks.resolve("tier1.json"), tier1.toString());
            SplitConfigFiles.load(directory, defaults, ready);
            JsonArray imported = read(ranks.resolve("tier1.json")).getAsJsonObject("equipment_loadout").getAsJsonArray("mainhand");
            check(imported.contains(row) && imported.contains(disabled) && imported.size() > custom.size(),
                    "enabled additive import preserves user entries and disabled weights");
            TierConfigProjection.put(global, "equipment_integration.auto_import_enabled", new JsonPrimitive(false));
            Files.writeString(ranks.resolve("global.json"), global.toString());
            byte[] completed = Files.readAllBytes(ranks.resolve("tier1.json"));
            SplitConfigFiles.load(directory, defaults, ready);
            check(Arrays.equals(completed, Files.readAllBytes(ranks.resolve("tier1.json"))), "turning off does not delete prior imports");
            check(EquipmentIntegrationDefaults.automaticImportsEnabled(new JsonObject()), "missing master keeps default enabled");
            for (JsonElement invalid : List.of(JsonNull.INSTANCE, new JsonPrimitive("yes"), new JsonObject())) {
                TierConfigProjection.put(global, "equipment_integration.auto_import_enabled", invalid);
                check(!EquipmentIntegrationDefaults.automaticImportsEnabled(global), "invalid master cannot authorize imports");
            }
            JsonObject root = defaults.deepCopy();
            TierConfigProjection.put(root, "equipment_integration.auto_import_enabled", new JsonPrimitive(false));
            check(!EquipmentIntegrationDefaults.seed(root, mod -> true, item -> true), "root import entry also honors master switch");
        } finally { cleanup(directory); }
    }

    private static void checkDefaultIntegrationRetry() throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        Path directory = Files.createTempDirectory("human-spartan-retry-");
        java.util.function.BiPredicate<String, JsonObject> unavailable = (rank, file) ->
                EquipmentIntegrationDefaults.seedRank(rank, file, TierConfigProjection.tier(defaults, rank),
                        mod -> true, item -> false);
        java.util.function.BiPredicate<String, JsonObject> ready = (rank, file) ->
                EquipmentIntegrationDefaults.seedRank(rank, file, TierConfigProjection.tier(defaults, rank),
                        mod -> true, item -> true);
        try {
            SplitConfigFiles.load(directory, defaults, unavailable);
            Path ranks = directory.resolve(SplitConfigFiles.DIRECTORY);
            byte[] global = Files.readAllBytes(ranks.resolve("global.json"));
            Map<String, byte[]> originals = new HashMap<>();
            for (String rank : TierConfigProjection.RANKS) {
                Path path = ranks.resolve(rank + ".json");
                JsonObject data = read(path);
                data.addProperty("_custom_note", "Preserve this note");
                data.getAsJsonObject("attributes").addProperty("max_health", 21);
                Files.writeString(path, data.toString());
                originals.put(rank, Files.readAllBytes(path));
                check(!data.getAsJsonObject("equipment_loadout").has("_imported_spartanweaponry"),
                        "unready registry does not finalize " + rank);
            }
            SplitConfigFiles.load(directory, defaults, ready);
            check(Arrays.equals(global, Files.readAllBytes(ranks.resolve("global.json"))), "global is byte-identical");
            for (String rank : TierConfigProjection.RANKS) {
                Path path = ranks.resolve(rank + ".json");
                JsonObject data = read(path), gear = data.getAsJsonObject("equipment_loadout");
                check(gear.get("_imported_spartanweaponry").getAsBoolean()
                        && gear.get("_imported_spartanshields").getAsBoolean(), "both imports persisted " + rank);
                for (String pool : List.of("mainhand", "inventory", "ranged_mainhand", "shield_pool"))
                    check(gear.getAsJsonArray(pool).size() > TierConfigProjection.tier(defaults, rank)
                            .getAsJsonObject("equipment_loadout").getAsJsonArray(pool).size(), "full stock pool seeded " + rank + pool);
                check(data.get("_custom_note").getAsString().equals("Preserve this note")
                        && data.getAsJsonObject("attributes").get("max_health").getAsInt() == 21,
                        "unrelated edits retained " + rank);
                try (var paths = Files.list(ranks)) {
                    check(paths.count() == 5, "import leaves exactly five files " + rank);
                }
                gear.add("mainhand", new JsonArray());
                Files.writeString(path, data.toString());
                SplitConfigFiles.load(directory, defaults, ready);
                check(read(path).getAsJsonObject("equipment_loadout").getAsJsonArray("mainhand").isEmpty(), "receipt preserves deleted entries " + rank);
            }
            for (String rank : TierConfigProjection.RANKS) {
                JsonObject file = TierConfigProjection.tier(defaults, rank);
                JsonObject gear = file.getAsJsonObject("equipment_loadout");
                for (String pool : List.of("mainhand", "inventory", "ranged_mainhand", "shield_pool", "armor_sets")) gear.add(pool, new JsonArray());
                check(!ready.test(rank, file), "explicit empty unimported pools not filled " + rank);
                file = TierConfigProjection.tier(defaults, rank);
                gear = file.getAsJsonObject("equipment_loadout");
                gear.addProperty("automatic_spartan_weapons", false);
                gear.addProperty("automatic_spartan_shields", false);
                gear.addProperty("automatic_immersive_armors", false);
                gear.addProperty("automatic_scguns_armor", false);
                check(!ready.test(rank, file), "disabled flags prevent import " + rank);
                file = TierConfigProjection.tier(defaults, rank);
                gear = file.getAsJsonObject("equipment_loadout");
                gear.add("automatic_spartan_weapons", JsonNull.INSTANCE);
                gear.add("automatic_spartan_shields", JsonNull.INSTANCE);
                gear.add("automatic_immersive_armors", JsonNull.INSTANCE);
                gear.add("automatic_scguns_armor", JsonNull.INSTANCE);
                check(!ready.test(rank, file), "malformed import flags preserved " + rank);
                file = TierConfigProjection.tier(defaults, rank);
                gear = file.getAsJsonObject("equipment_loadout");
                gear.getAsJsonArray("mainhand").get(0).getAsJsonObject().addProperty("weight", 987);
                JsonArray custom = gear.getAsJsonArray("mainhand").deepCopy();
                check(ready.test(rank, file) && gear.getAsJsonArray("mainhand").size() > custom.size()
                        && gear.getAsJsonArray("mainhand").get(0).equals(custom.get(0)),
                        "additive import retains customized row and weight " + rank);
                file = TierConfigProjection.tier(defaults, rank);
                check(!EquipmentIntegrationDefaults.seedRank(rank, file, TierConfigProjection.tier(defaults, rank),
                        mod -> false, item -> true), "absent mods do not import " + rank);
            }
            Path changed = ranks.resolve("tier1.json");
            byte[] stale = Files.readAllBytes(changed);
            Files.writeString(changed, "{\"schema_version\":2,\"_external_edit\":true}");
            byte[] current = Files.readAllBytes(changed);
            check(!SplitConfigFiles.writeCurrent(changed, stale, TierConfigProjection.tier(defaults, "tier1"))
                    && Arrays.equals(current, Files.readAllBytes(changed)), "stale snapshot cannot overwrite concurrent edit");
            Path broken = ranks.resolve("tier2.json");
            Files.writeString(broken, "not json");
            SplitConfigFiles.load(directory, defaults, ready);
            check(Files.readString(broken).equals("not json"), "malformed file cannot be rewritten by import");
        } finally { cleanup(directory); }
    }

    private static void checkCurrentLayoutOnly() throws Exception {
        Path fresh = Files.createTempDirectory("human-current-fresh-");
        try {
            SplitConfigFiles.load(fresh, UnifiedConfig.defaults(), (rank, data) -> false);
            checkFiveFiles(fresh, 5);
        } finally { cleanup(fresh); }
        Path directory = Files.createTempDirectory("human-current-layout-");
        try {
            Path ranks = directory.resolve(SplitConfigFiles.DIRECTORY);
            Files.createDirectories(ranks);
            List<Path> oldSources = new ArrayList<>();
            for (String old : List.of("hostile_humans.toml", "hostile_humans_unified.json", "humangunner.json", "humangunner_ai.json"))
                oldSources.add(directory.resolve(old));
            for (String old : List.of("equipment.json", "tiers.json", "spawning.json", "combat.json", "recruitment.json", "tacz.json", "compatibility.json"))
                oldSources.add(ranks.resolve(old));
            for (Path old : oldSources) Files.writeString(old, "broken obsolete config");
            JsonObject defaults = UnifiedConfig.defaults();
            JsonObject loaded = SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            checkFiveFiles(directory, 12);
            for (Path old : oldSources) check(Files.readString(old).equals("broken obsolete config"), "old source never touched " + old);
            check(new UnifiedConfig(loaded).tier("tier1").equals(new UnifiedConfig(defaults).tier("tier1")), "obsolete sources do not change gameplay defaults");
            try (var paths = Files.list(ranks)) { check(paths.count() == 12, "only five current files added beside seven existing old files"); }
            Path tier = ranks.resolve("tier1.json");
            JsonObject custom = json("{\"schema_version\":2,\"_custom_note\":\"keep\",\"recruitment\":{\"payment\":null},\"ammunition\":{\"arrows\":{\"min\":5}},\"equipment_loadout\":{\"mainhand\":[]},\"attributes\":{\"max_health\":31}}");
            Files.writeString(tier, custom.toString());
            SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            JsonObject saved = read(tier);
            check(saved.get("_custom_note").getAsString().equals("keep")
                    && TierConfigProjection.at(saved, "attributes.max_health").getAsInt() == 31
                    && TierConfigProjection.at(saved, "equipment_loadout.mainhand").getAsJsonArray().isEmpty()
                    && TierConfigProjection.at(saved, "ammunition.arrows.min").getAsInt() == 5,
                    "help refresh preserves explicit gameplay values, custom notes and empty pools");
            List<String> keys = new ArrayList<>(read(tier).keySet());
            check(keys.indexOf("attributes") < keys.indexOf("equipment_loadout") && !keys.contains("recruitment"), "existing custom file is reordered");
            byte[] stable = Files.readAllBytes(tier);
            SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            check(Arrays.equals(stable, Files.readAllBytes(tier)), "second reload does not rewrite layout");
            check(!read(tier).getAsJsonObject("ammunition").getAsJsonObject("arrows").has("max"), "in-memory defaults are not persisted");
            JsonObject emptyMaps = json("{\"recruitment\":{\"payment_by_tier\":{\"tier1\":{}}},\"tacz\":{\"gun_whitelist\":{},\"hired_gun_additional_whitelist\":{},\"tier_gun_type_weights\":{\"tier1\":{}}}}");
            CurrentConfigDefaults.fill(emptyMaps, defaults, "");
            for (String path : List.of("recruitment.payment_by_tier.tier1", "tacz.gun_whitelist", "tacz.hired_gun_additional_whitelist", "tacz.tier_gun_type_weights.tier1"))
                check(TierConfigProjection.at(emptyMaps, path).getAsJsonObject().size() == 0, "custom empty map retained " + path);
            for (JsonObject file : List.of(TierConfigProjection.global(defaults), TierConfigProjection.tier(defaults, "tier1"))) {
                boolean rank = file.has("attributes");
                JsonObject ordered = TierConfigProjection.arrange(file, rank);
                check(file.equals(ordered), "layout contains identical fields and values");
                check(ordered.toString().equals(TierConfigProjection.arrange(ordered, rank).toString()), "layout order is idempotent");
            }
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

    private static void checkFiveFiles(Path directory, int expectedJsonFiles) throws Exception {
        for (String file : SplitConfigFiles.FILES) check(Files.isRegularFile(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(file)), "current file exists " + file);
        try (var paths = Files.list(directory.resolve(SplitConfigFiles.DIRECTORY))) {
            check(paths.filter(path -> path.toString().endsWith(".json")).count() == expectedJsonFiles, "only current five JSON files added, preserving existing obsolete files");
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
