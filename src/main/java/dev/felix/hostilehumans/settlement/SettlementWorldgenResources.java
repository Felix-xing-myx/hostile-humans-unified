package dev.felix.hostilehumans.settlement;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Compiles an approved catalog into vanilla registry resources plus HHU site-selection rules. */
final class SettlementWorldgenResources {
    private static final Gson JSON = new Gson();
    private static final String NAMESPACE = HumanRuntime.MOD_ID;
    static final int MAX_HORIZONTAL_SIDE = 112;

    private record GroupKey(String id, SettlementBuildingPack.Group placement, JsonArray entries) {}

    private SettlementWorldgenResources() {}

    static Map<String, byte[]> compile(Map<ResourceLocation, SettlementBuildingPack.Contents> catalog,
            Set<ResourceLocation> approved) {
        var files = new TreeMap<String, byte[]>();
        var groups = new TreeMap<String, GroupKey>();
        var lootIds = new HashSet<ResourceLocation>();
        for (var id : new java.util.TreeSet<>(catalog.keySet())) {
            var pack = catalog.get(id);
            var generation = pack.generation();
            var author = pack.author();
            if (author.kind() == SettlementTemplate.Kind.ATTACHMENT
                    || author.size().x() > MAX_HORIZONTAL_SIDE || author.size().z() > MAX_HORIZONTAL_SIDE) {
                if (approved.contains(id)) HumanRuntime.LOGGER.warn("Building pack {} can only be placed manually", id);
                continue;
            }
            String path = "building_pack/" + id.getNamespace() + "/" + id.getPath();
            String generatedId = NAMESPACE + ":" + path;
            // Keep the structure and template registrable after disabling new starts.
            files.put(resource(NAMESPACE, "structures", path, ".nbt"), pack.template());
            putJson(files, resource(NAMESPACE, "worldgen/structure", path, ".json"),
                    structure(generatedId, generation.rules()));
            addLoot(files, lootIds, pack.loot());
            if (!approved.contains(id) || !generation.rules().enabled()) continue;
            var group = groups.computeIfAbsent(generation.rules().group(), key ->
                    new GroupKey(key, generation.group(), new JsonArray()));
            if (!group.placement().equals(generation.group()))
                throw new IllegalArgumentException("inconsistent placement settings for " + group.id());

            var entry = new JsonObject();
            entry.addProperty("structure", generatedId);
            entry.addProperty("weight", generation.rules().weight());
            group.entries().add(entry);
        }
        for (var group : groups.values()) {
            var id = new ResourceLocation(group.id());
            var set = new JsonObject();
            set.add("structures", group.entries());
            var placement = new JsonObject();
            placement.addProperty("type", "minecraft:random_spread");
            placement.addProperty("spacing", group.placement().spacing());
            placement.addProperty("separation", group.placement().separation());
            placement.addProperty("salt", group.placement().salt());
            set.add("placement", placement);
            putJson(files, resource(NAMESPACE, "worldgen/structure_set",
                    "building_pack/" + id.getNamespace() + "/" + id.getPath(), ".json"), set);
        }
        return Map.copyOf(files);
    }

    private static JsonObject structure(String id, SettlementGenerationRules rules) {
        var data = new JsonObject();
        data.addProperty("type", NAMESPACE + ":building_pack");
        // The custom type applies allow/exclude selectors at the actual candidate anchor.
        data.add("biomes", new JsonArray());
        data.add("spawn_overrides", new JsonObject());
        data.addProperty("step", rules.placement() == SettlementGenerationRules.Placement.SURFACE
                ? "surface_structures" : "underground_structures");
        data.addProperty("terrain_adaptation", rules.terrain().name().toLowerCase(java.util.Locale.ROOT));
        data.addProperty("template", id);
        data.add("rules", JSON.toJsonTree(rules));
        return data;
    }

    private static void addLoot(Map<String, byte[]> files, Set<ResourceLocation> ids,
            Map<ResourceLocation, JsonObject> loot) {
        for (var entry : loot.entrySet()) {
            var id = entry.getKey();
            if (!ids.add(id)) {
                HumanRuntime.LOGGER.warn("Ignoring duplicate building pack loot table {}", id);
                continue;
            }
            putJson(files, resource(id.getNamespace(), "loot_tables", id.getPath(), ".json"), entry.getValue());
        }
    }

    private static String resource(String namespace, String directory, String path, String extension) {
        return "data/" + namespace + "/" + directory + "/" + path + extension;
    }

    private static void putJson(Map<String, byte[]> files, String path, JsonObject value) {
        files.put(path, JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
}
