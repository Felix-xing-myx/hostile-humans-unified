package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable birth pool selection and high-priority equipment bans, separated by provider identity. */
final class GunSpawnPools {
    static final List<String> BACKENDS = List.of("tacz", "scguns");
    private final Map<String, Backend> backends;

    private record Pool(boolean empty, Map<ResourceLocation, Integer> weights) {
        private static final Pool EMPTY = new Pool(true, Map.of());
        private static final Pool DISABLED = new Pool(false, Map.of());
    }

    private record Backend(boolean tierSource, boolean automatic, int automaticWeight,
                           Set<String> excludedTypes, Set<String> excludedNamespaces, Set<String> blacklist,
                           Pool global, Map<String, Pool> tiers) { }

    private GunSpawnPools(Map<String, Backend> backends) { this.backends = Map.copyOf(backends); }

    static GunSpawnPools parse(JsonObject root) {
        Map<String, Backend> result = new LinkedHashMap<>();
        for (String backend : BACKENDS) {
            JsonObject settings = UnifiedConfig.object(root, backend);
            String source = UnifiedConfig.string(settings, "spawn_pool_source", backend.equals("tacz") ? "global" : "tier");
            boolean valid = source.equals("global") || source.equals("tier");
            if (!valid) HumanGunner.LOGGER.warn("Invalid {}.spawn_pool_source {}; disabling its spawn pool", backend, source);
            Map<String, Pool> tiers = new LinkedHashMap<>();
            for (String rank : TierConfigProjection.RANKS) {
                String path = backend.equals("tacz") ? "_tier_file_metadata." + rank + ".tacz.gun_whitelist"
                        : "tiers." + rank + ".scguns.gun_whitelist";
                tiers.put(rank, weights(TierConfigProjection.at(root, path)));
            }
            result.put(backend, new Backend(source.equals("tier"), valid && UnifiedConfig.bool(settings, "auto_add_new_guns", true),
                    (int) UnifiedConfig.number(settings, "auto_added_gun_weight", 5, 0, Integer.MAX_VALUE),
                    strings(settings.get("excluded_auto_gun_types")), strings(settings.get("excluded_gun_namespaces")),
                    strings(settings.get("gun_blacklist")), valid ? weights(settings.get("gun_whitelist")) : Pool.DISABLED,
                    valid ? Map.copyOf(tiers) : Map.of()));
        }
        return new GunSpawnPools(result);
    }

    Map<GunSupport.GunChoice, Integer> select(String rank, Map<String, Map<ResourceLocation, String>> catalogs) {
        if (!TierConfigProjection.RANKS.contains(rank)) return Map.of();
        Map<GunSupport.GunChoice, Integer> selected = new LinkedHashMap<>();
        for (String name : BACKENDS) {
            Backend backend = backends.get(name);
            Pool pool = backend.tierSource ? backend.tiers.getOrDefault(rank, Pool.DISABLED) : backend.global;
            boolean discover = pool.empty && backend.automatic;
            catalogs.getOrDefault(name, Map.of()).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ResourceLocation id = entry.getKey();
                if (backend.blacklist.contains(id.toString()) || backend.excludedNamespaces.contains(id.getNamespace())) return;
                int weight = pool.weights.getOrDefault(id, 0);
                if (discover && !backend.excludedTypes.contains(entry.getValue())) weight = backend.automaticWeight;
                if (weight > 0) selected.put(new GunSupport.GunChoice(name, id), weight);
            });
        }
        return Collections.unmodifiableMap(selected);
    }

    boolean allows(String backend, ResourceLocation id) {
        Backend settings = backends.get(backend);
        return settings != null && id != null && !settings.blacklist.contains(id.toString());
    }

    private static Pool weights(JsonElement input) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        if (input == null) return Pool.EMPTY;
        if (!input.isJsonObject()) {
            // Malformed authored lists fail closed, rather than becoming an automatic empty pool.
            return Pool.DISABLED;
        }
        for (var entry : input.getAsJsonObject().entrySet()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Expected gun ID and nonnegative integer weight");
                double value = entry.getValue().getAsDouble();
                if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.floor(value))
                    throw new IllegalArgumentException("Invalid weight");
                result.put(id, (int) value);
            } catch (RuntimeException error) {
                HumanGunner.LOGGER.warn("Ignoring invalid birth gun entry {}", entry.getKey());
            }
        }
        return new Pool(input.getAsJsonObject().size() == 0, Map.copyOf(result));
    }

    private static Set<String> strings(JsonElement input) {
        Set<String> result = new HashSet<>();
        if (input != null && input.isJsonArray()) for (JsonElement entry : input.getAsJsonArray())
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) result.add(entry.getAsString());
        return Set.copyOf(result);
    }
}
