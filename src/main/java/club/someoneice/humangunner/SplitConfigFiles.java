package club.someoneice.humangunner;

import com.google.gson.*;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/** Startup-only storage boundary. Gameplay retains one immutable, merged settings snapshot. */
final class SplitConfigFiles {
    static final String DIRECTORY = "hostile_humans_unified";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private record Module(String file, String chinese, String english, List<String> sections) { }
    private static final List<Module> MODULES = List.of(
            new Module("tiers.json", "各阶属性、移动、伤害与武器散布", "Tier attributes, movement, damage and weapon accuracy", List.of("tiers")),
            new Module("spawning.json", "自然生成、密度、新手保护与分阶日期", "Natural spawning, density, beginner protection and tier dates", List.of("spawning")),
            new Module("combat.json", "战斗 AI、恢复行为与通用伤害", "Combat AI, recovery and common damage", List.of("damage", "ai")),
            new Module("recruitment.json", "雇佣费用、上限与玩家间伤害", "Hiring costs, limits and player-versus-player damage", List.of("recruitment")),
            new Module("tacz.json", "枪械兼容、概率、白名单与权重", "Firearm integration, chances, whitelists and weights", List.of("tacz")),
            new Module("compatibility.json", "可选模组兼容开关", "Optional mod integration switches", List.of("better_combat")));

    private SplitConfigFiles() { }

    static JsonObject load(Path configDirectory) {
        Path directory = configDirectory.resolve(DIRECTORY);
        Path legacy = configDirectory.resolve("hostile_humans_unified.json");
        JsonObject defaults = UnifiedConfig.defaults();
        JsonObject seed = new JsonObject();
        boolean canGenerate = true;
        try {
            if (Files.exists(legacy)) {
                seed = read(legacy);
            } else if (MODULES.stream().noneMatch(module -> Files.exists(directory.resolve(module.file)))) {
                seed = UnifiedConfig.migrate(readIfPresent(configDirectory.resolve("humangunner.json")),
                        readIfPresent(configDirectory.resolve("humangunner_ai.json")));
            }
        } catch (Exception error) {
            canGenerate = false;
            LogUtils.getLogger().error("Cannot read legacy human config; missing modules use defaults without generating replacement files", error);
        }
        boolean missing = MODULES.stream().anyMatch(module -> !Files.exists(directory.resolve(module.file)));
        if (canGenerate && missing && Files.exists(legacy)) {
            try {
                Path backup = configDirectory.resolve("hostile_humans_unified.json.pre-split.bak");
                if (!Files.exists(backup)) Files.copy(legacy, backup);
            } catch (IOException error) {
                canGenerate = false;
                LogUtils.getLogger().error("Cannot back up legacy human config; postponing split-file generation", error);
            }
        }

        JsonObject result = new JsonObject();
        for (Module module : MODULES) {
            Path path = directory.resolve(module.file);
            JsonObject data;
            if (Files.exists(path)) {
                try {
                    data = read(path);
                    boolean changed = enrich(data, module, defaults);
                    if (changed) writeSafely(path, data, true);
                } catch (Exception error) {
                    LogUtils.getLogger().error("Cannot load human config module {}; using legacy/default values for this module without overwriting it", path, error);
                    data = project(seed, module, defaults);
                }
            } else {
                data = project(seed, module, defaults);
                if (canGenerate) {
                    try {
                        Files.createDirectories(directory);
                        write(path, data, false);
                    } catch (FileAlreadyExistsException concurrentWriter) {
                        try { data = read(path); }
                        catch (Exception error) { LogUtils.getLogger().error("Cannot read concurrently created human config {}", path, error); }
                    } catch (IOException error) {
                        LogUtils.getLogger().error("Cannot create human config module {}; using in-memory settings", path, error);
                    }
                }
            }
            for (String section : module.sections) {
                if (data.has(section)) result.add(section, data.get(section).deepCopy());
            }
        }
        return result;
    }

    private static JsonObject project(JsonObject seed, Module module, JsonObject defaults) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", 1);
        for (String section : module.sections) {
            result.add(section, (seed.has(section) ? seed.get(section) : defaults.get(section)).deepCopy());
        }
        enrich(result, module, defaults);
        return result;
    }

    private static boolean enrich(JsonObject data, Module module, JsonObject defaults) {
        boolean changed = false;
        if (!data.has("schema_version")) { data.addProperty("schema_version", 1); changed = true; }
        if (!data.has("_说明_中文")) { data.addProperty("_说明_中文", module.chinese + "。修改后重启生效；本文件优先于旧单文件配置。"); changed = true; }
        if (!data.has("_description_en")) { data.addProperty("_description_en", module.english + ". Restart to apply. This file takes precedence over the legacy single-file config."); changed = true; }
        for (String section : module.sections) {
            if (!data.has(section)) {
                data.add(section, defaults.get(section).deepCopy());
                changed = true;
            } else if (data.get(section).isJsonObject()) {
                changed |= addDescriptions(data.getAsJsonObject(section), defaults.getAsJsonObject(section));
            }
        }
        // Add genuine new options without filling all legacy tier fields:
        // inserting modern numeric defaults would override deprecated-but-supported aliases.
        if (module.sections.contains("spawning") && data.get("spawning").isJsonObject()) {
            changed |= UnifiedConfig.addMissingSettings(data.getAsJsonObject("spawning"), "progression",
                    defaults.getAsJsonObject("spawning").getAsJsonObject("progression"));
        }
        if (module.sections.contains("better_combat")) {
            changed |= UnifiedConfig.addMissingSettings(data, "better_combat", defaults.getAsJsonObject("better_combat"));
        }
        return changed;
    }

    private static boolean addDescriptions(JsonObject data, JsonObject defaults) {
        boolean changed = false;
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_") && !data.has(key)) {
                data.add(key, entry.getValue().deepCopy());
                changed = true;
            } else if (entry.getValue().isJsonObject() && data.has(key) && data.get(key).isJsonObject()) {
                changed |= addDescriptions(data.getAsJsonObject(key), entry.getValue().getAsJsonObject());
            }
        }
        return changed;
    }

    private static JsonObject readIfPresent(Path path) throws IOException {
        return Files.exists(path) ? read(path) : new JsonObject();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            if (data.has("schema_version") && data.get("schema_version").getAsInt() != 1)
                throw new IOException("Unsupported config schema; input left unchanged: " + path);
            return data;
        }
    }

    private static void writeSafely(Path path, JsonObject data, boolean replace) {
        try { write(path, data, replace); }
        catch (IOException error) { LogUtils.getLogger().warn("Cannot add missing descriptions/options to {}; in-memory settings remain active", path, error); }
    }

    private static void write(Path path, JsonObject data, boolean replace) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "human-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { GSON.toJson(data, writer); }
            if (!replace) Files.move(temporary, path);
            else {
                try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
