package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record HumanGunnerConfig(
        double tier1GunChance,
        double tier2GunChance,
        double tier3GunChance,
        double roamerGunChance,
        double forcedRangedChance,
        double gunDropChance,
        double pillagerGunDamageMultiplier,
        Map<String, Map<String, Integer>> tierGunTypeWeights
) {
    private static final String PATH = "hostile_humans_unified/global.json: tacz";
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

    /**
     * Selects a gun category for a newly created gunner, then applies the
     * existing per-gun whitelist weights inside that category. Categories
     * with no currently registered whitelist gun are omitted and the
     * remaining configured percentages are renormalized automatically.
     */
    public Optional<GunSupport.GunChoice> rollAvailableGun(RandomSource random, String tierKey) {
        Map<String, Map<ResourceLocation, String>> catalogs = GunSupport.get().catalogsByBackend();
        Map<GunSupport.GunChoice, Integer> allGuns = UnifiedConfig.get().gunSpawnPools().select(tierKey, catalogs);
        Map<String, Integer> configuredTypes = tierGunTypeWeights.get(tierKey);
        if (configuredTypes == null || configuredTypes.isEmpty()) {
            return rollWeightedGun(random, allGuns);
        }

        LinkedHashMap<String, LinkedHashMap<GunSupport.GunChoice, Integer>> gunsByType =
                new LinkedHashMap<>();
        for (Map.Entry<GunSupport.GunChoice, Integer> entry : allGuns.entrySet()) {
            String type = catalogs.getOrDefault(entry.getKey().backend(), Map.of()).getOrDefault(entry.getKey().id(), "");
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
            LinkedHashMap<GunSupport.GunChoice, Integer> typedGuns = gunsByType.get(entry.getKey());
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

    private static Optional<GunSupport.GunChoice> rollWeightedGun(
            RandomSource random, Map<GunSupport.GunChoice, Integer> availableGuns
    ) {
        long totalWeight = availableGuns.values().stream().mapToLong(Integer::longValue).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }

        double roll = random.nextDouble() * totalWeight;
        for (Map.Entry<GunSupport.GunChoice, Integer> entry : availableGuns.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public long availableGunCount(String rank) { return availableGuns(rank).size(); }

    private Map<GunSupport.GunChoice, Integer> availableGuns(String rank) {
        return UnifiedConfig.get().gunSpawnPools().select(rank, GunSupport.get().catalogsByBackend());
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
                    : 0.4D;
            double tier3Chance = root.has("tier3_gun_chance")
                    ? root.get("tier3_gun_chance").getAsDouble() : 0.7D;
            double forcedChance = root.has("forced_ranged_gun_chance")
                    ? root.get("forced_ranged_gun_chance").getAsDouble() : 0.33D;
            double roamerChance = root.has("roamer_gun_chance")
                    ? root.get("roamer_gun_chance").getAsDouble() : 0.08D;
            double dropChance = root.has("gun_drop_chance")
                    ? root.get("gun_drop_chance").getAsDouble() : 0.01D;
            double pillagerDamageMultiplier = root.has("pillager_gun_damage_multiplier")
                    ? root.get("pillager_gun_damage_multiplier").getAsDouble() : 0.4D;
            Map<String, Map<String, Integer>> typeWeights = readTierGunTypeWeights(root);
            return new HumanGunnerConfig(
                    clamp(tier1Chance),
                    clamp(chance),
                    clamp(tier3Chance),
                    clamp(roamerChance),
                    clamp(forcedChance),
                    clamp(dropChance),
                    clampDamageMultiplier(pillagerDamageMultiplier),
                    typeWeights
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
                0.4D,
                defaultTierGunTypeWeights()
        );
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

    private static double clampDamageMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(10.0D, value)) : 1.0D;
    }
}
