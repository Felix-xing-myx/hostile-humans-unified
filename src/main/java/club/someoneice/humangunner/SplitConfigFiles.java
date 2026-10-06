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
        // No old source is inspected if all five new files already exist. A broken old
        // document cannot disable a fully migrated installation or restore deleted values.
        boolean missing = FILES.stream().anyMatch(file -> !Files.exists(directory.resolve(file)));
        LegacySplitConfigFiles.Loaded legacy = missing ? LegacySplitConfigFiles.load(configDirectory, defaults)
                : new LegacySplitConfigFiles.Loaded(defaults.deepCopy(), Set.of(), Set.of());
        boolean backedUp = !missing || LegacySplitConfigFiles.backup(legacy);
        JsonObject result = loadFile(directory.resolve("global.json"), TierConfigProjection.global(legacy.root()),
                TierConfigProjection.global(defaults), backedUp && legacy.canGenerate("global.json"), false, integrations);
        // Discard recognized per-tier values accidentally left in a global file. This
        // avoids a second settings source and makes each rank independently authoritative.
        result = TierConfigProjection.global(result);
        boolean automaticImports = EquipmentIntegrationDefaults.automaticImportsEnabled(result);
        java.util.function.BiPredicate<String, JsonObject> permittedIntegrations =
                (rank, data) -> automaticImports && integrations.test(rank, data);
        for (String rank : TierConfigProjection.RANKS) {
            JsonObject fallback = TierConfigProjection.tier(legacy.root(), rank);
            JsonObject data = loadFile(directory.resolve(rank + ".json"), fallback,
                    TierConfigProjection.tier(defaults, rank), backedUp && legacy.canGenerate(rank + ".json"), true, permittedIntegrations);
            TierConfigProjection.applyTier(result, rank, data);
        }
        result.addProperty("schema_version", 2);
        return result;
    }

    private static JsonObject loadFile(Path path, JsonObject fallback, JsonObject defaults, boolean generate, boolean rank,
                                       java.util.function.BiPredicate<String, JsonObject> integrations) {
        if (Files.exists(path)) return existing(path, defaults, rank, integrations);
        JsonObject data = fallback.deepCopy();
        TierConfigProjection.fill(data, defaults, "");
        describe(data, rank);
        if (rank && generate) integrations.test(rankName(path), data);
        data = TierConfigProjection.arrange(data, rank);
        if (generate) {
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
            if (rank && integrations.test(rankName(path), data)) {
                // Only a successful first additive equipment import may persist.
                // Custom values/notes are retained, and the exact source bytes are backed up.
                if (!writeImported(path, original, data)) data = read(path, 2);
            }
            TierConfigProjection.fill(data, defaults, "");
            describe(data, rank);
            return data;
        } catch (Exception invalid) {
            LogUtils.getLogger().error("Cannot load human configuration {}; leaving input untouched and using defaults in memory", path, invalid);
            JsonObject data = defaults.deepCopy();
            if (rank) TierConfigProjection.put(data, "recruitment.payment", JsonNull.INSTANCE);
            else TierConfigProjection.put(data, "equipment_integration.auto_import_enabled", new JsonPrimitive(false));
            return data;
        }
    }

    private static String rankName(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    static boolean writeImported(Path path, byte[] original, JsonObject data) {
        Path temporary = null;
        try {
            if (!Arrays.equals(original, Files.readAllBytes(path))) return false;
            temporary = Files.createTempFile(path.getParent(), "human-equipment-import-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(data, writer); }
            Path backup = Files.createTempFile(path.getParent(), path.getFileName() + ".pre-equipment-import-", ".bak");
            Files.write(backup, original);
            if (!Arrays.equals(original, Files.readAllBytes(backup))) throw new IOException("Import backup mismatch");
            if (!Arrays.equals(original, Files.readAllBytes(path))) return false;
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            LogUtils.getLogger().info("Imported available default equipment into {}; previous configuration backed up at {}", path, backup);
            return true;
        } catch (IOException error) {
            LogUtils.getLogger().error("Cannot persist equipment import into {}; existing file remains authoritative", path, error);
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
                ? "本阶属性、装备、弹药、战斗 AI、生成、枪械与招聘设置。修改后重启生效；本文件优先于旧布局。"
                : "全局开关、伤害、自然生成、装备成长、招聘与兼容设置。修改后重启生效；分阶设置在各阶文件中。" );
        if (!data.has("_description_en")) data.addProperty("_description_en", rank
                ? "This rank's attributes, equipment, ammunition, AI, spawning, firearms and hiring. Restart to apply; this file takes precedence over old layouts."
                : "Global switches, damage, spawning, equipment growth, hiring and integrations. Restart to apply; rank options live in the rank files.");
    }

    static JsonObject read(Path path, int version) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IOException("Configuration root must be an object: " + path);
            JsonObject data = parsed.getAsJsonObject();
            JsonElement schema = data.get("schema_version");
            // Legacy schema omission is supported; new files must explicitly declare v2.
            if (schema == null && version == 1) return data;
            if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()
                    || !schema.getAsBigDecimal().equals(java.math.BigDecimal.valueOf(version)))
                throw new IOException("Unsupported config schema; input left unchanged: " + path);
            return data;
        }
    }

    static void writeNew(Path path, JsonObject data) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "human-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(data, writer); }
            // A move with no REPLACE_EXISTING must fail if another process creates the
            // target. Do not use ATOMIC_MOVE: its replacement behavior is provider-specific.
            Files.move(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }
}
