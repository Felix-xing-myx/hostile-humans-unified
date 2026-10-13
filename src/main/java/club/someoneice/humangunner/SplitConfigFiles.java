package club.someoneice.humangunner;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Startup-only five-file storage boundary; existing v2 files always own their settings. */
final class SplitConfigFiles {
    static final String DIRECTORY = "hostile_humans_unified";
    static final List<String> FILES = List.of("global.json", "roamer.json", "tier1.json", "tier2.json", "tier3.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private SplitConfigFiles() { }

    static JsonObject load(Path configDirectory) {
        return load(configDirectory, UnifiedConfig.defaults());
    }

    static JsonObject load(Path configDirectory, JsonObject defaults) {
        return load(configDirectory, defaults, (rank, data) -> EquipmentIntegrationDefaults.seedRank(
                rank, data, TierConfigProjection.tier(defaults, rank)));
    }

    static JsonObject load(Path configDirectory, JsonObject defaults,
                           java.util.function.BiPredicate<String, JsonObject> integrations) {
        Path directory = configDirectory.resolve(DIRECTORY);
        GrowthProfileFiles.relocate(directory, defaults);
        JsonObject result = loadFile(directory.resolve("global.json"), TierConfigProjection.global(defaults),
                false, integrations);
        // Discard recognized per-tier values accidentally left in a global file. This
        // avoids a second settings source and makes each rank independently authoritative.
        result = TierConfigProjection.global(result);
        boolean automaticImports = EquipmentIntegrationDefaults.automaticImportsEnabled(result);
        java.util.function.BiPredicate<String, JsonObject> permittedIntegrations =
                (rank, data) -> automaticImports && integrations.test(rank, data);
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject fallback = TierConfigProjection.tier(defaults, rank);
            JsonObject data = loadFile(directory.resolve(rank + ".json"), fallback,
                    true, permittedIntegrations);
            TierConfigProjection.applyTier(result, rank, data);
        }
        result.addProperty("schema_version", 2);
        return result;
    }

    private static JsonObject loadFile(Path path, JsonObject defaults, boolean rank,
                                       java.util.function.BiPredicate<String, JsonObject> integrations) {
        if (Files.exists(path)) return existing(path, defaults, rank, integrations);
        JsonObject data = defaults.deepCopy();
        TierConfigProjection.fill(data, defaults, "");
        describe(data, rank);
        if (rank) integrations.test(rankName(path), data);
        data = TierConfigProjection.arrange(data, rank);
        {
            try {
                Files.createDirectories(path.getParent());
                writeNew(path, data);
            } catch (FileAlreadyExistsException concurrentWriter) {
                // Re-read the winner, including its version and payment failure handling.
                return existing(path, defaults, rank, integrations);
            } catch (IOException error) {
                LogUtils.getLogger().error("Cannot create human configuration {}; using in-memory settings", path, error);
            }
        }
        return data;
    }

    private static JsonObject existing(Path path, JsonObject defaults, boolean rank,
                                       java.util.function.BiPredicate<String, JsonObject> integrations) {
        try {
            byte[] original = Files.readAllBytes(path);
            JsonObject data = read(path, 2);
            boolean imported = rank && integrations.test(rankName(path), data);
            // Split direct arrow damage into independent bow/crossbow controls; preserve explicit new values.
            if (rank) {
                JsonObject damage = data.has("damage_multipliers") && data.get("damage_multipliers").isJsonObject()
                        ? data.getAsJsonObject("damage_multipliers") : new JsonObject();
                if (!data.has("damage_multipliers")) data.add("damage_multipliers", damage);
                JsonObject template = defaults.getAsJsonObject("damage_multipliers");
                for (String key : List.of("bow_damage_multiplier", "crossbow_damage_multiplier"))
                    if (!damage.has(key)) damage.add(key, template.get(key).deepCopy());
            }
            // Expose the independent backend factor in existing rank files without copying TaCZ values.
            if (rank && !data.has("scguns")) data.add("scguns", defaults.getAsJsonObject("scguns").deepCopy());
            if (!rank) {
                if (data.has("growth") && data.get("growth").isJsonObject()) {
                    JsonObject growth = data.getAsJsonObject("growth");
                    if (!growth.has("damage_stage_percent")) {
                        JsonObject template = defaults.getAsJsonObject("growth");
                        for (String key : List.of("damage_stage_percent", "_说明_damage_stage_percent_中文", "_description_damage_stage_percent_en"))
                            growth.add(key, template.get(key).deepCopy());
                    }
                }
                JsonObject scguns = data.has("scguns") && data.get("scguns").isJsonObject()
                        ? data.getAsJsonObject("scguns") : null;
                if (scguns != null && !scguns.has("human_gun_damage_multiplier"))
                    scguns.add("human_gun_damage_multiplier", defaults.getAsJsonObject("scguns").get("human_gun_damage_multiplier").deepCopy());
                for (String key : List.of("jump_attack", "first_aid", "scguns", "shield_pressure", "armor_swap", "combat_resupply")) {
                    if (!data.has(key) && defaults.has(key)) {
                        JsonObject section = defaults.getAsJsonObject(key).deepCopy();
                        if (key.equals("jump_attack") && TierConfigProjection.at(data, "damage.jump_attack_damage_multiplier") != null)
                            section.add("damage_multiplier", TierConfigProjection.at(data, "damage.jump_attack_damage_multiplier").deepCopy());
                        data.add(key, section);
                    }
                }
            }
            if (!rank && data.has("shield_pressure") && data.get("shield_pressure").isJsonObject()) {
                JsonObject pressure = data.getAsJsonObject("shield_pressure");
                JsonObject template = defaults.getAsJsonObject("shield_pressure");
                for (String key : List.of("max_guard_ticks", "break_attempt_ticks"))
                    if (!pressure.has(key)) pressure.add(key, template.get(key).deepCopy());
            }
            if (rank && data.has("equipment_loadout") && data.get("equipment_loadout").isJsonObject()) {
                JsonObject loadout = data.getAsJsonObject("equipment_loadout");
                if (!loadout.has("automatic_scguns_armor")) loadout.addProperty("automatic_scguns_armor", true);
            }
            // Add only the new pool controls; never merge a player's explicit pool.
            for (String backend : GunSpawnPools.CONFIG_SECTIONS) {
                JsonObject section = data.has(backend) && data.get(backend).isJsonObject()
                        ? data.getAsJsonObject(backend) : new JsonObject();
                if (!data.has(backend)) data.add(backend, section);
                JsonObject template = defaults.getAsJsonObject(backend);
                if (!rank && backend.equals("tacz") && !section.has("inventory_fed_capacity"))
                    section.add("inventory_fed_capacity", template.get("inventory_fed_capacity").deepCopy());
                for (String key : rank ? List.of("gun_whitelist") : List.of("spawn_pool_source", "gun_blacklist",
                        "gun_whitelist", "auto_add_new_guns", "auto_added_gun_weight", "excluded_auto_gun_types",
                        "excluded_gun_namespaces", "jeg_gun_types", "jeg_inventory_fed_capacity")) {
                    if (!section.has(key) && template.has(key)) section.add(key, template.get(key).deepCopy());
                }
            }
            TacticalSupplyDefaults.refresh(data, defaults, rank);
            TierConfigProjection.refreshGuidance(data, defaults);
            JsonObject arranged = TierConfigProjection.arrange(data, rank);
            byte[] output = format(arranged).getBytes(StandardCharsets.UTF_8);
            // Refresh help/ownership and expose new feature sections; other missing values
            // remain in memory and explicit pools stay authoritative.
            if ((imported || !Arrays.equals(original, output)) && !writeCurrent(path, original, arranged))
                data = read(path, 2);
            else data = arranged;
            TierConfigProjection.fill(data, defaults, "");
            describe(data, rank);
            return data;
        } catch (Exception invalid) {
            LogUtils.getLogger().error("Cannot load human configuration {}; leaving input untouched and using defaults in memory", path, invalid);
            JsonObject data = defaults.deepCopy();
            if (!rank) {
                TierConfigProjection.put(data, "recruitment.payment_by_tier", JsonNull.INSTANCE);
                TierConfigProjection.put(data, "equipment_integration.auto_import_enabled", new JsonPrimitive(false));
            }
            return data;
        }
    }

    private static String rankName(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    static boolean writeCurrent(Path path, byte[] original, JsonObject data) {
        Path temporary = null;
        try {
            if (!Arrays.equals(original, Files.readAllBytes(path))) return false;
            temporary = Files.createTempFile(path.getParent(), "human-config-save-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { writer.write(format(data)); }
            if (!Arrays.equals(original, Files.readAllBytes(path))) return false;
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            LogUtils.getLogger().info("Saved current human configuration layout/equipment in {}", path);
            return true;
        } catch (IOException error) {
            LogUtils.getLogger().error("Cannot save current configuration into {}; existing file remains authoritative", path, error);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanup) { LogUtils.getLogger().warn("Cannot remove equipment import temporary file {}", temporary, cleanup); }
            }
        }
    }

    private static void describe(JsonObject data, boolean rank) {
        if (!data.has("_说明_中文")) data.addProperty("_说明_中文", rank
                ? "本阶属性、装备、弹药、战斗 AI、生成与枪械设置。修改后重启生效；本阶常用设置在前，细节参数在后。"
                : "全局开关、伤害、自然生成、统一成长、招聘与兼容设置。修改后重启生效；分阶设置在各阶文件中。" );
        if (!data.has("_description_en")) data.addProperty("_description_en", rank
                ? "This rank's attributes, equipment, ammunition, AI, spawning and firearms. Restart to apply; common settings precede detailed tuning."
                : "Global switches, damage, spawning, unified growth, hiring and integrations. Restart to apply; rank options live in the rank files.");
    }

    static JsonObject read(Path path, int version) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IOException("Configuration root must be an object: " + path);
            JsonObject data = parsed.getAsJsonObject();
            JsonElement schema = data.get("schema_version");
            if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                    || !schema.getAsBigDecimal().equals(java.math.BigDecimal.valueOf(version)))
                throw new IOException("Unsupported config schema; input left unchanged: " + path);
            return data;
        }
    }

    /** Strict JSON with a blank line between described controls; no editor-specific syntax. */
    static String format(JsonObject data) {
        StringBuilder result = new StringBuilder();
        String previous = "";
        for (String line : GSON.toJson(data).split("\\n")) {
            String trimmed = line.stripLeading();
            boolean fieldHelp = trimmed.startsWith("\"_说明_") && !trimmed.startsWith("\"_说明_中文\"");
            if (fieldHelp && !previous.isEmpty() && !previous.endsWith("{")) result.append('\n');
            result.append(line).append('\n');
            previous = line.stripTrailing();
        }
        return result.toString();
    }

    static void writeNew(Path path, JsonObject data) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "human-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { writer.write(format(data)); }
            // A move with no REPLACE_EXISTING must fail if another process creates the
            // target. Do not use ATOMIC_MOVE: its replacement behavior is provider-specific.
            Files.move(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }
}
