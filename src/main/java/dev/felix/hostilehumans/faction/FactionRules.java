package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.progression.SpecialtyConfig;
import dev.felix.hostilehumans.progression.SpecialtyData;
import dev.felix.hostilehumans.faction.StandingIncidents;

import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.*;
import java.util.*;

/** Global adjustable policy, parsed once with the five-file configuration; no world or registry access. */
public record FactionRules(boolean legacyMapping, int dailyGainCap, int groupRadius,
                    StandingIncidents.Settings incidents, List<Integer> ownBonus, List<Integer> enemyPenalty,
                    double bossBudgetMultiplier, double bossAllocationMax, Map<String, Double> bossBases,
                    double bossKnockbackAdd, double bossDeviationCap) {
    public FactionRules { ownBonus = List.copyOf(ownBonus); enemyPenalty = List.copyOf(enemyPenalty); bossBases = Map.copyOf(bossBases); }
    /** Above the configured same-rank ordinary ceiling, never above a merely sampled mean. */
    public double bossBudget(SpecialtyConfig specialty, String tier) {
        return specialty.budgetMax() * specialty.tierBudgetMultiplier(tier) * bossBudgetMultiplier;
    }
    public double allocationMaximum(SpecialtyConfig specialty) {
        return Math.max(specialty.multiplierMax(), bossAllocationMax);
    }
    /** Birth allocation and the detail panel share this policy; ordinary per-dimension overrides stay intact. */
    public double[] allocationCaps(SpecialtyConfig specialty, SpecialtyData.Definitions definitions, boolean boss) {
        return definitions.caps(specialty.deviationCap(), bossDeviationCap, boss, false);
    }
    public static FactionRules parse(JsonObject root) {
        JsonObject faction = UnifiedConfig.object(root, "factions"), standing = UnifiedConfig.object(root, "standing"),
                band = UnifiedConfig.object(root, "identity_band"), boss = UnifiedConfig.object(root, "boss");
        var settings = new StandingIncidents.Settings(integer(band, "hit_window_ticks", 300, 20, 1200),
                integer(band, "hit_limit", 5, 1, 10), integer(band, "damage_window_ticks", 800, 40, 1200),
                UnifiedConfig.number(band, "damage_fraction", .30, .05, 1), integer(band, "anger_ticks", 600, 100, 2400),
                integer(band, "group_ticks", 600, 100, 2400), integer(band, "cross_tier_window_ticks", 100, 20, 400),
                integer(band, "cross_tier_hits", 2, 1, 5));
        Map<String, Double> bases = new HashMap<>();
        bases.put("health", UnifiedConfig.number(boss, "health_multiplier", 1.8, 1, 10));
        bases.put("armor", UnifiedConfig.number(boss, "armor_multiplier", 1.3, 1, 10));
        bases.put("toughness", UnifiedConfig.number(boss, "toughness_multiplier", 1.3, 1, 10));
        bases.put("speed", UnifiedConfig.number(boss, "speed_multiplier", .95, .1, 5));
        bases.put("detection", UnifiedConfig.number(boss, "detection_multiplier", 1.1, .1, 5));
        bases.put("melee", UnifiedConfig.number(boss, "damage_multiplier", 1.15, 1, 5));
        bases.put("gun", UnifiedConfig.number(boss, "gun_multiplier", 1.1, .1, 5));
        bases.put("accuracy", 1 / UnifiedConfig.number(boss, "spread_multiplier", .95, .1, 5));
        return new FactionRules(UnifiedConfig.bool(faction, "legacy_tier_mapping", true),
                integer(standing, "daily_gain_cap", 30, 0, 1_000_000), integer(band, "group_radius", 25, 8, 64), settings,
                four(band, "own_bonus", List.of(50, 75, 100, 150)), four(band, "enemy_penalty", List.of(100, 150, 200, 250)),
                UnifiedConfig.number(boss, "budget_multiplier", 1.10, 1.01, 1.5),
                UnifiedConfig.number(boss, "allocation_multiplier_max", 3.0, 1.8, 3),
                bases, UnifiedConfig.number(boss, "knockback_add", .20, 0, 1),
                UnifiedConfig.number(boss, "deviation_cap", .90, 0, 1));
    }
    int bandModifier(boolean same, boolean enemies, int grade) {
        return grade < 1 || grade > 4 ? 0 : same ? ownBonus.get(grade - 1) : enemies ? -enemyPenalty.get(grade - 1) : 0;
    }
    private static int integer(JsonObject data, String key, int fallback, int min, int max) {
        return (int) UnifiedConfig.number(data, key, fallback, min, max);
    }
    private static List<Integer> four(JsonObject data, String key, List<Integer> fallback) {
        if (!data.has(key)) return fallback;
        try {
            JsonArray values = data.getAsJsonArray(key);
            if (values.size() != 4) throw new IllegalArgumentException("four grades required");
            List<Integer> result = new ArrayList<>();
            for (JsonElement raw : values) {
                int value = raw.getAsBigDecimal().intValueExact();
                if (value < 0 || value > 1000) throw new IllegalArgumentException("invalid modifier");
                result.add(value);
            }
            return List.copyOf(result);
        } catch (RuntimeException invalid) {
            com.mojang.logging.LogUtils.getLogger().warn("Invalid identity_band.{}; using default four-grade modifiers", key);
            return fallback;
        }
    }
}
