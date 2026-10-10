package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Move current user settings before layout refresh; no aliases or inheritance files. */
final class GrowthProfileFiles {
    private record RankFile(Path path, byte[] original, JsonObject data) {}
    private GrowthProfileFiles() {}

    static void relocate(Path directory, JsonObject defaults) {
        Path globalPath = directory.resolve("global.json");
        if (!Files.isRegularFile(globalPath)) return;
        try {
            byte[] original = Files.readAllBytes(globalPath);
            JsonObject global = SplitConfigFiles.read(globalPath, 2);
            JsonObject growth = UnifiedConfig.object(global, "growth");
            JsonObject profiles = UnifiedConfig.object(growth, "profiles");
            Map<String, RankFile> writes = new LinkedHashMap<>();
            // Validate every destination first. Never remove source data on partial failure.
            for (String rank : TierConfigProjection.RANKS) {
                if (!profiles.has(rank)) continue;
                if (!profiles.get(rank).isJsonObject()) throw new IllegalArgumentException("Invalid profile " + rank);
                Path path = directory.resolve(rank + ".json");
                boolean exists = Files.exists(path);
                byte[] bytes = exists ? Files.readAllBytes(path) : null;
                JsonObject data = exists ? SplitConfigFiles.read(path, 2) : TierConfigProjection.tier(defaults, rank);
                if (!exists) data.remove("growth_profile");
                if (data.has("growth_profile") && !data.get("growth_profile").isJsonObject())
                    throw new IllegalArgumentException("Invalid destination growth_profile in " + path);
                JsonObject profile = data.has("growth_profile") ? data.getAsJsonObject("growth_profile") : new JsonObject();
                // An explicit destination field wins; omitted fields retain the previous custom value.
                for (var entry : profiles.getAsJsonObject(rank).entrySet())
                    if (!profile.has(entry.getKey())) profile.add(entry.getKey(), entry.getValue().deepCopy());
                data.add("growth_profile", profile);
                writes.put(rank, new RankFile(path, bytes, TierConfigProjection.arrange(data, true)));
            }
            if (writes.isEmpty()) return;
            for (RankFile file : writes.values()) {
                if (file.original() == null) SplitConfigFiles.writeNew(file.path(), file.data());
                else if (!SplitConfigFiles.writeCurrent(file.path(), file.original(), file.data())) return;
            }
            for (String rank : writes.keySet()) profiles.remove(rank);
            if (profiles.size() == 0) growth.remove("profiles");
            // Compare-and-write: if the user edits global meanwhile, keep it for a safe retry.
            SplitConfigFiles.writeCurrent(globalPath, original, global);
        } catch (Exception error) {
            LogUtils.getLogger().error("Cannot relocate current growth profiles; retaining source settings for retry in {}", globalPath, error);
        }
    }
}
