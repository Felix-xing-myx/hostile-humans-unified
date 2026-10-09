package club.someoneice.humangunner;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Filesystem and projection regressions; can be run as a standalone JavaExec main. */
public final class TierConfigFilesTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        checkImmersiveArmorImport();
        checkMasterIntegrationSwitch();
        checkDefaultIntegrationRetry();
        checkProjection();
        checkRetiredControls();
        checkReadableDefaults();
        checkBrokenNewFilesAndPayments();
        checkCurrentLayoutOnly();
        checkCreationCollision();
        System.out.println("TierConfigFilesTest: " + assertions + " checks passed");
    }

    private static void checkRetiredControls() throws Exception {
        var dir = Files.createTempDirectory("human-retired-controls-");
        try {
            var defaults = UnifiedConfig.defaults();
            SplitConfigFiles.load(dir, defaults, (rank, data) -> false);
            var globalPath = dir.resolve(SplitConfigFiles.DIRECTORY).resolve("global.json");
            var global = JsonParser.parseString(Files.readString(globalPath)).getAsJsonObject();
            global.getAsJsonObject("native_gameplay").addProperty("no_waystones", true);
            var damage = global.getAsJsonObject("damage");
            damage.addProperty("human_bow_damage_multiplier", 8);
            damage.addProperty("bow_velocity_damage_multiplier", 9);
            damage.addProperty("_description_bow_velocity_damage_multiplier_en", "retired");
            damage.addProperty("arrow_damage_multiplier", 72);
            damage.addProperty("custom_extension", 42);
            Files.writeString(globalPath, global.toString());
            var rankPath = dir.resolve(SplitConfigFiles.DIRECTORY).resolve("tier1.json");
            var rank = JsonParser.parseString(Files.readString(rankPath)).getAsJsonObject();
            rank.getAsJsonObject("potions").add("bonus", json("{\"chance\":1}"));
            rank.getAsJsonObject("damage_multipliers").addProperty("bow_damage_multiplier", 9);
            rank.getAsJsonObject("damage_multipliers").addProperty("arrow_damage_multiplier", 2);
            Files.writeString(rankPath, rank.toString());
            var loaded = new UnifiedConfig(SplitConfigFiles.load(dir, defaults, (r, data) -> false));
            check(loaded.damage("arrow_damage_multiplier", 0) == 72 && loaded.tier("tier1").bowDamage() == 2,
                    "combined range and explicit current tier value survive disk cleanup");
            global = JsonParser.parseString(Files.readString(globalPath)).getAsJsonObject();
            damage = global.getAsJsonObject("damage");
            check(!damage.has("human_bow_damage_multiplier") && !damage.has("bow_velocity_damage_multiplier")
                    && !damage.has("_description_bow_velocity_damage_multiplier_en"), "retired global keys and comments removed on disk");
            check(damage.get("custom_extension").getAsInt() == 42, "unknown extension preserved");
            check(!global.getAsJsonObject("native_gameplay").has("no_waystones"), "inactive Waystones switch removed");
            rank = JsonParser.parseString(Files.readString(rankPath)).getAsJsonObject();
            check(!rank.getAsJsonObject("potions").has("bonus")
                    && !rank.getAsJsonObject("damage_multipliers").has("bow_damage_multiplier"), "retired rank controls removed on disk");
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
        check(!sparseWeights.getAsJsonObject("recruitment").getAsJsonObject("payment").has("item"), "invalid partial payment not silently repaired");
        sparseWeights.getAsJsonObject("tacz").addProperty("extension_option", 23);
        TierConfigProjection.applyTier(global, "tier1", sparseWeights);
        check(TierConfigProjection.tier(global, "tier1").getAsJsonObject("tacz").get("extension_option").getAsInt() == 23,
                "unknown grouped rank option round trips through internal metadata");
    }

    private static void checkReadableDefaults() throws Exception {
        JsonObject defaults = UnifiedConfig.defaults();
        JsonObject global = TierConfigProjection.global(defaults);
        check(!global.has("tiers") && !global.has("equipment_loadouts"), "no orphan rank/loadout guidance in global");
        check(TierConfigProjection.at(global, "spawning.progression.first_spawn_day_by_tier") == null,
                "no orphan rank unlock guidance in global");
        check(!global.getAsJsonObject("equipment_growth").has("_description_item_ids_en"), "growth item guidance moves to ranks");
        check(!global.getAsJsonObject("tacz").has("_description_gun_type_weights_en"), "type-weight guidance moves to ranks");
        JsonObject custom = defaults.deepCopy();
        custom.getAsJsonObject("tiers").addProperty("_description_custom_extension_en", "Keep my custom notes");
        check(TierConfigProjection.global(custom).getAsJsonObject("tiers").has("_description_custom_extension_en"),
                "unknown custom guidance is not discarded");
        TierConfigProjection.put(custom, "equipment_loadouts.tier1", JsonNull.INSTANCE);
        TierConfigProjection.put(custom, "equipment_growth.tiers.tier1", JsonNull.INSTANCE);
        TierConfigProjection.put(custom, "tiers.tier1.damage_multipliers", JsonNull.INSTANCE);
        JsonObject invalid = TierConfigProjection.tier(custom, "tier1");
        check(invalid.get("equipment_loadout").isJsonNull() && invalid.get("equipment_growth").isJsonNull()
                && invalid.get("damage_multipliers").isJsonNull(), "guidance does not replace explicit invalid values");
        Path directory = Files.createTempDirectory("human-readable-config-");
        try {
            JsonObject assembled = SplitConfigFiles.load(directory, defaults);
            UnifiedConfig baseline = new UnifiedConfig(defaults), projected = new UnifiedConfig(assembled);
            for (String rank : TierConfigProjection.RANKS) {
                JsonObject file = SplitConfigFiles.read(directory.resolve(SplitConfigFiles.DIRECTORY).resolve(rank + ".json"), 2);
                List<String> sections = new ArrayList<>(file.keySet());
                check(sections.get(0).equals("schema_version"), "schema first " + rank);
                check(sections.indexOf("attributes") < sections.indexOf("ai")
                        && sections.indexOf("equipment_loadout") < sections.indexOf("ai")
                        && sections.indexOf("equipment_loadout") < sections.indexOf("ammunition"), "consistent section order " + rank);
                for (String path : List.of("attributes.health_min", "movement.base_speed", "ai.retreat_health_ratio",
                        "equipment_loadout.ranged_chance", "equipment_growth.start_day", "ammunition.arrows.min",
                        "potions.recovery.count_min", "tacz.gun_chance", "recruitment.max_hired", "spawning.first_spawn_day")) {
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
                data.getAsJsonObject("attributes").addProperty("health_min", 21);
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
                        && data.getAsJsonObject("attributes").get("health_min").getAsInt() == 21,
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
                check(!ready.test(rank, file), "disabled flags prevent import " + rank);
                file = TierConfigProjection.tier(defaults, rank);
                gear = file.getAsJsonObject("equipment_loadout");
                gear.add("automatic_spartan_weapons", JsonNull.INSTANCE);
                gear.add("automatic_spartan_shields", JsonNull.INSTANCE);
                gear.add("automatic_immersive_armors", JsonNull.INSTANCE);
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
            JsonObject custom = json("{\"schema_version\":2,\"_custom_note\":\"keep\",\"recruitment\":{\"payment\":null},\"ammunition\":{\"arrows\":{\"min\":5}},\"equipment_loadout\":{\"mainhand\":[]},\"attributes\":{\"health_min\":31}}");
            Files.writeString(tier, custom.toString());
            SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            check(read(tier).equals(custom), "layout-only save preserves every explicit value and absent key");
            List<String> keys = new ArrayList<>(read(tier).keySet());
            check(keys.indexOf("attributes") < keys.indexOf("equipment_loadout") && keys.indexOf("equipment_loadout") < keys.indexOf("recruitment"), "existing custom file is reordered");
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
