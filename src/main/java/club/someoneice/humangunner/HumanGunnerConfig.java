package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record HumanGunnerConfig(
        double tier1GunChance,
        double tier2GunChance,
        double tier3GunChance,
        double roamerGunChance,
        double forcedRangedChance,
        double gunDropChance,
        double humanGunDamageMultiplier,
        double pillagerGunDamageMultiplier,
        double gunnerSpreadDegrees,
        boolean autoAddNewGuns,
        int autoAddedGunWeight,
        Set<String> excludedAutoGunTypes,
        Set<String> excludedGunNamespaces,
        Map<String, Map<String, Integer>> tierGunTypeWeights,
        Map<ResourceLocation, Integer> gunWhitelist,
        Map<ResourceLocation, Integer> hiredGunAdditionalWhitelist
) {
    private static final String PATH = "hostile_humans_unified.json: tacz";
    private static volatile HumanGunnerConfig instance;

    public static HumanGunnerConfig get() {
        HumanGunnerConfig value = instance;
        if (value == null) {
            synchronized (HumanGunnerConfig.class) {
                value = instance;
                if (value == null) {
                    instance = value = load();
                }
            }
        }
        return value;
    }

    public Optional<ResourceLocation> rollAvailableGun(RandomSource random) {
        return rollWeightedGun(random, availableGuns());
    }

    /**
     * Selects a gun category for a newly created gunner, then applies the
     * existing per-gun whitelist weights inside that category. Categories
     * with no currently registered whitelist gun are omitted and the
     * remaining configured percentages are renormalized automatically.
     */
    public Optional<ResourceLocation> rollAvailableGun(RandomSource random, String tierKey) {
        Map<String, Integer> configuredTypes = tierGunTypeWeights.get(tierKey);
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            return rollAvailableGun(random);
        }

        Map<ResourceLocation, String> catalog = GunSupport.get().catalog();
        Map<ResourceLocation, Integer> allGuns = availableGuns(catalog);
        LinkedHashMap<String, LinkedHashMap<ResourceLocation, Integer>> gunsByType =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : allGuns.entrySet()) {
            String type = catalog.getOrDefault(entry.getKey(), "");
            if (configuredTypes.getOrDefault(type, 0) > 0) {
                gunsByType.computeIfAbsent(type, ignored -> new LinkedHashMap<>())
                        .put(entry.getKey(), entry.getValue());
            }
        }

        long totalTypeWeight = configuredTypes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && gunsByType.containsKey(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        if (totalTypeWeight <= 0) {
            return Optional.empty();
        }

        double typeRoll = random.nextDouble() * totalTypeWeight;
        for (Map.Entry<String, Integer> entry : configuredTypes.entrySet()) {
            LinkedHashMap<ResourceLocation, Integer> typedGuns = gunsByType.get(entry.getKey());
            if (entry.getValue() <= 0 || typedGuns == null || typedGuns.isEmpty()) {
                continue;
            }
            typeRoll -= entry.getValue();
            if (typeRoll < 0) {
                return rollWeightedGun(random, typedGuns);
            }
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> rollWeightedGun(
            RandomSource random, Map<ResourceLocation, Integer> availableGuns
    ) {
        long totalWeight = availableGuns.values().stream().mapToLong(Integer::longValue).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }

        double roll = random.nextDouble() * totalWeight;
        for (Map.Entry<ResourceLocation, Integer> entry : availableGuns.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public long availableGunCount() {
        return availableGuns().size();
    }

    private Map<ResourceLocation, Integer> availableGuns() {
        return availableGuns(GunSupport.get().catalog());
    }

    private Map<ResourceLocation, Integer> availableGuns(Map<ResourceLocation, String> catalog) {
        return selectAvailable(catalog, UnifiedConfig.get()::blacklisted);
    }

    Map<ResourceLocation, Integer> selectAvailable(Map<ResourceLocation, String> catalog,
                                                 java.util.function.Predicate<String> blacklisted) {
        LinkedHashMap<ResourceLocation, Integer> available = new LinkedHashMap<>();
        catalog.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = entry.getKey();
            if (excludedGunNamespaces.contains(id.getNamespace()) || blacklisted.test(id.toString())) return;
            Integer explicit = gunWhitelist.get(id);
            // Explicit zero and blacklist both win over automatic discovery.
            if (explicit != null) {
                if (explicit > 0) available.put(id, explicit);
            } else if (autoAddNewGuns && autoAddedGunWeight > 0 && !excludedAutoGunTypes.contains(entry.getValue())) {
                available.put(id, autoAddedGunWeight);
            }
        });
        return available;
    }

    private static HumanGunnerConfig load() {
        return parse(UnifiedConfig.get().gunConfig());
    }

    static HumanGunnerConfig parse(JsonObject root) {
        try {
            double tier1Chance = root.has("tier1_gun_chance")
                    ? root.get("tier1_gun_chance").getAsDouble() : 0.1D;
            double chance = root.has("tier2_gun_chance")
                    ? root.get("tier2_gun_chance").getAsDouble()
                    : root.has("globe") ? root.get("globe").getAsDouble() : 0.4D;
            double tier3Chance = root.has("tier3_gun_chance")
                    ? root.get("tier3_gun_chance").getAsDouble() : 0.7D;
            double forcedChance = root.has("forced_ranged_gun_chance")
                    ? root.get("forced_ranged_gun_chance").getAsDouble() : 0.33D;
            double roamerChance = root.has("roamer_gun_chance")
                    ? root.get("roamer_gun_chance").getAsDouble() : 0.08D;
            double dropChance = root.has("gun_drop_chance")
                    ? root.get("gun_drop_chance").getAsDouble() : 0.01D;
            double damageMultiplier = root.has("human_gun_damage_multiplier")
                    ? root.get("human_gun_damage_multiplier").getAsDouble() : 0.5D;
            double pillagerDamageMultiplier = root.has("pillager_gun_damage_multiplier")
                    ? root.get("pillager_gun_damage_multiplier").getAsDouble() : 0.4D;
            double spreadDegrees = root.has("gunner_spread_degrees")
                    ? root.get("gunner_spread_degrees").getAsDouble() : 0.6D;
            boolean autoAddNew = root.has("auto_add_new_guns")
                    ? root.get("auto_add_new_guns").getAsBoolean()
                    : !root.has("include_unlisted_guns") || root.get("include_unlisted_guns").getAsBoolean();
            int autoAddedWeight = root.has("auto_added_gun_weight")
                    ? Math.max(0, root.get("auto_added_gun_weight").getAsInt())
                    : root.has("unlisted_gun_weight")
                    ? Math.max(0, root.get("unlisted_gun_weight").getAsInt()) : 5;
            LinkedHashSet<String> excludedTypes = new LinkedHashSet<>();
            if (root.has("excluded_auto_gun_types")) {
                root.getAsJsonArray("excluded_auto_gun_types")
                        .forEach(element -> excludedTypes.add(element.getAsString()));
            } else if (root.has("excluded_unlisted_gun_types")) {
                root.getAsJsonArray("excluded_unlisted_gun_types")
                        .forEach(element -> excludedTypes.add(element.getAsString()));
            } else {
                excludedTypes.add("rpg");
            }
            LinkedHashSet<String> excludedNamespaces = new LinkedHashSet<>();
            if (root.has("excluded_gun_namespaces")) {
                root.getAsJsonArray("excluded_gun_namespaces")
                        .forEach(element -> excludedNamespaces.add(element.getAsString()));
            }
            Map<String, Map<String, Integer>> typeWeights = readTierGunTypeWeights(root);
            LinkedHashMap<ResourceLocation, Integer> weights = new LinkedHashMap<>();
            JsonObject gunObject = root.has("gun_whitelist")
                    ? root.getAsJsonObject("gun_whitelist")
                    : root.getAsJsonObject("guns");
            readGunWeights(gunObject, "gun_whitelist", weights);
            if (gunObject == null) {
                weights.putAll(defaults().gunWhitelist());
            }
            LinkedHashMap<ResourceLocation, Integer> hiredAdditionalWeights = new LinkedHashMap<>();
            JsonObject hiredAdditionalObject = root.has("hired_gun_additional_whitelist")
                    ? root.getAsJsonObject("hired_gun_additional_whitelist")
                    : null;
            readGunWeights(hiredAdditionalObject, "hired_gun_additional_whitelist", hiredAdditionalWeights);
            return new HumanGunnerConfig(
                    clamp(tier1Chance),
                    clamp(chance),
                    clamp(tier3Chance),
                    clamp(roamerChance),
                    clamp(forcedChance),
                    clamp(dropChance),
                    clampDamageMultiplier(damageMultiplier),
                    clampDamageMultiplier(pillagerDamageMultiplier),
                    clampSpread(spreadDegrees),
                    autoAddNew,
                    autoAddedWeight,
                    Set.copyOf(excludedTypes),
                    Set.copyOf(excludedNamespaces),
                    typeWeights,
                    Map.copyOf(weights),
                    Map.copyOf(hiredAdditionalWeights)
            );
        } catch (Exception exception) {
            HumanGunner.LOGGER.error("Could not read {}; using defaults", PATH, exception);
            return defaults();
        }
    }

    private static HumanGunnerConfig defaults() {
        return new HumanGunnerConfig(
                0.1D,
                0.4D,
                0.7D,
                0.08D,
                0.33D,
                0.01D,
                0.5D,
                0.4D,
                0.6D,
                false,
                5,
                Set.of("rpg"),
                Set.of(),
                defaultTierGunTypeWeights(),
                Map.of(),
                Map.of()
        );
    }

    private static void readGunWeights(
            JsonObject object, String configKey, Map<ResourceLocation, Integer> destination
    ) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                ResourceLocation gunId = ResourceLocation.tryParse(entry.getKey());
                if (gunId == null) {
                    throw new IllegalArgumentException("Invalid resource location");
                }
                destination.put(gunId, Math.max(0, entry.getValue().getAsInt()));
            } catch (RuntimeException exception) {
                HumanGunner.LOGGER.warn(
                        "Ignoring invalid Human Gunner config entry {}.{}",
                        configKey, entry.getKey()
                );
            }
        }
    }

    private static Map<String, Map<String, Integer>> readTierGunTypeWeights(JsonObject root) {
        Map<String, Map<String, Integer>> defaults = defaultTierGunTypeWeights();
        if (!root.has("tier_gun_type_weights")
                || !root.get("tier_gun_type_weights").isJsonObject()) {
            return defaults;
        }

        JsonObject configured = root.getAsJsonObject("tier_gun_type_weights");
        LinkedHashMap<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> defaultTier : defaults.entrySet()) {
            String tierKey = defaultTier.getKey();
            if (!configured.has(tierKey) || !configured.get(tierKey).isJsonObject()) {
                result.put(tierKey, defaultTier.getValue());
                continue;
            }
            LinkedHashMap<String, Integer> row = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry
                    : configured.getAsJsonObject(tierKey).entrySet()) {
                try {
                    row.put(
                            entry.getKey().toLowerCase(java.util.Locale.ROOT),
                            Math.max(0, entry.getValue().getAsInt())
                    );
                } catch (RuntimeException exception) {
                    HumanGunner.LOGGER.warn(
                            "Ignoring invalid gun type weight {}.{}",
                            tierKey, entry.getKey()
                    );
                }
            }
            long total = row.values().stream().mapToLong(Integer::longValue).sum();
            if (total <= 0) {
                HumanGunner.LOGGER.warn(
                        "Gun type weights for {} are empty; using defaults", tierKey
                );
                result.put(tierKey, defaultTier.getValue());
            } else {
                if (total != 100) {
                    HumanGunner.LOGGER.warn(
                            "Gun type weights for {} total {} instead of 100; treating them as relative weights",
                            tierKey, total
                    );
                }
                result.put(tierKey, immutableWeightRow(row));
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, Map<String, Integer>> defaultTierGunTypeWeights() {
        LinkedHashMap<String, Map<String, Integer>> result = new LinkedHashMap<>();
        result.put("roamer", immutableWeightRow(new LinkedHashMap<>(Map.of(
                "pistol", 60,
                "smg", 30,
                "shotgun", 10
        ))));
        result.put("tier1", immutableWeightRow(new LinkedHashMap<>(Map.of(
                "pistol", 30,
                "smg", 40,
                "shotgun", 20,
                "sniper", 10
        ))));
        result.put("tier2", immutableWeightRow(new LinkedHashMap<>(Map.of(
                "pistol", 20,
                "smg", 25,
                "shotgun", 15,
                "rifle", 20,
                "mg", 10,
                "sniper", 10
        ))));
        result.put("tier3", immutableWeightRow(new LinkedHashMap<>(Map.of(
                "pistol", 0,
                "smg", 10,
                "rifle", 40,
                "sniper", 5,
                "mg", 20,
                "shotgun", 25
        ))));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, Integer> immutableWeightRow(Map<String, Integer> row) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row));
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private static double clampSpread(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(10.0D, value)) : 1.0D;
    }

    private static double clampDamageMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(10.0D, value)) : 1.0D;
    }
}
