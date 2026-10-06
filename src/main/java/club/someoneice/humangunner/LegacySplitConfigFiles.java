package club.someoneice.humangunner;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only compatibility reader. Never creates or rewrites the obsolete seven modules. */
final class LegacySplitConfigFiles {
    private static final Map<String, List<String>> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("equipment.json", List.of("equipment_loadouts", "equipment_growth"));
        MODULES.put("tiers.json", List.of("tiers"));
        MODULES.put("spawning.json", List.of("spawning"));
        MODULES.put("combat.json", List.of("damage", "ai"));
        MODULES.put("recruitment.json", List.of("recruitment"));
        MODULES.put("tacz.json", List.of("tacz"));
        MODULES.put("compatibility.json", List.of("better_combat"));
    }
    record Loaded(JsonObject root, Set<String> blockedSections, Set<Path> sources) {
        boolean canGenerate(String file) {
            // All tiers consume old AI, spawning, equipment, recruitment and firearm regions.
            Set<String> needed = file.equals("global.json")
                    ? Set.of("native_gameplay", "damage", "ai", "spawning", "equipment_growth", "recruitment", "tacz", "better_combat")
                    : Set.of("native_gameplay", "tiers", "equipment_loadouts", "equipment_growth", "spawning", "ai", "recruitment", "tacz");
            return Collections.disjoint(needed, blockedSections);
        }
    }
    private LegacySplitConfigFiles() { }

    static Loaded load(Path configDirectory, JsonObject defaults) {
        JsonObject root = new JsonObject();
        Set<String> blocked = new HashSet<>();
        Set<Path> sources = new LinkedHashSet<>();
        Path tomlPath = configDirectory.resolve("hostile_humans.toml");
        JsonObject toml = new JsonObject();
        if (Files.exists(tomlPath)) {
            sources.add(tomlPath);
            try { toml = LegacyTomlConfig.read(configDirectory); }
            catch (Exception invalid) { blocked.addAll(List.of("native_gameplay", "ai")); log(tomlPath, invalid); }
        }
        Path single = configDirectory.resolve("hostile_humans_unified.json");
        if (Files.exists(single)) {
            sources.add(single);
            try { root = SplitConfigFiles.read(single, 1); }
            catch (Exception invalid) {
                for (List<String> sections : MODULES.values()) blocked.addAll(sections);
                log(single, invalid);
            }
        } else {
            JsonObject guns = readOld(configDirectory.resolve("humangunner.json"), sources, blocked, List.of("tacz", "damage"));
            JsonObject ai = readOld(configDirectory.resolve("humangunner_ai.json"), sources, blocked, List.of("ai"));
            root = UnifiedConfig.migrate(guns, ai);
            // migrate() returns defaults as well as explicit old settings. Its defaults
            // must not conceal a still-effective custom value from the older TOML.
            root.remove("native_gameplay");
            for (String key : UnifiedConfig.object(toml, "ai").keySet()) {
                if (!ai.has(key)) UnifiedConfig.object(root, "ai").remove(key);
            }
        }
        Path directory = configDirectory.resolve(SplitConfigFiles.DIRECTORY);
        for (var module : MODULES.entrySet()) {
            Path file = directory.resolve(module.getKey());
            if (!Files.exists(file)) continue;
            sources.add(file);
            try {
                JsonObject data = SplitConfigFiles.read(file, 1);
                ConfigSchemaUpgrade.aliases(data, defaults);
                for (String section : module.getValue()) {
                    // A present module owns its entire section, even when that section is absent.
                    root.add(section, (data.has(section) ? data.get(section) : defaults.get(section)).deepCopy());
                    blocked.remove(section);
                }
                JsonObject metadata = data.deepCopy();
                for (String section : module.getValue()) metadata.remove(section);
                metadata.remove("schema_version");
                if (metadata.size() != 0) TierConfigProjection.put(root, "_legacy_module_metadata." + module.getKey().replace(".json", ""), metadata);
            } catch (Exception invalid) {
                blocked.addAll(module.getValue());
                log(file, invalid);
            }
        }
        TierConfigProjection.fill(root, toml, "");
        ConfigSchemaUpgrade.aliases(root, defaults);
        JsonElement payments = TierConfigProjection.at(root, "recruitment.payment_by_tier");
        JsonObject explicitPayments = payments != null && payments.isJsonObject() ? payments.getAsJsonObject().deepCopy() : new JsonObject();
        ConfigSchemaUpgrade.fill(root, defaults, "");
        for (String rank : TierConfigProjection.RANKS) {
            if (explicitPayments.has(rank))
                TierConfigProjection.put(root, "recruitment.payment_by_tier." + rank, explicitPayments.get(rank));
        }
        if (blocked.contains("recruitment")) {
            TierConfigProjection.put(root, "recruitment.payment_by_tier", JsonNull.INSTANCE);
        }
        // Integration discovery runs in SplitConfigFiles only after the authoritative
        // global file (including its master switch) has been loaded. No legacy bypass.
        return new Loaded(root, Set.copyOf(blocked), Set.copyOf(sources));
    }

    private static JsonObject readOld(Path path, Set<Path> sources, Set<String> blocked, List<String> sections) {
        if (!Files.exists(path)) return new JsonObject();
        sources.add(path);
        try { return SplitConfigFiles.read(path, 1); }
        catch (Exception invalid) { blocked.addAll(sections); log(path, invalid); return new JsonObject(); }
    }
    private static void log(Path path, Exception invalid) {
        LogUtils.getLogger().error("Cannot read legacy human configuration {}; source remains untouched and dependent migration is postponed", path, invalid);
    }

    static boolean backup(Loaded loaded) {
        for (Path path : loaded.sources) {
            String suffix = path.getFileName().toString().equals("hostile_humans.toml") ? ".pre-tier.bak" : ".pre-tier-layout.bak";
            Path backup = path.resolveSibling(path.getFileName() + suffix);
            try {
                if (!Files.exists(backup)) {
                    try { Files.copy(path, backup); }
                    catch (FileAlreadyExistsException concurrentBackup) { /* First successful backup wins. */ }
                }
                if (!Files.isRegularFile(backup)) throw new IOException("Backup target is not a file: " + backup);
            } catch (IOException error) {
                LogUtils.getLogger().error("Cannot back up legacy human configuration {}; postponing new layout generation", path, error);
                return false;
            }
        }
        return true;
    }
}
