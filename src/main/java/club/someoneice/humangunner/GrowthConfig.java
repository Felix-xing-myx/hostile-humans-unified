package club.someoneice.humangunner;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;

/** Immutable global growth settings. Percentages use 100 = full base value. */
public final class GrowthConfig {
    private final boolean enabled;
    private final List<Double> stagePercent, stageCost;
    private final double experienceMinPercent, experienceMaxPercent, experienceCapPercent, experienceCost;
    private final double damageUnit, rewardUnit, baseRewardCap, bonusDamageThreshold, bonusReward;
    private final EquipmentGrowthPolicy equipmentGrowth;

    private GrowthConfig(JsonObject data, JsonObject defaults) {
        enabled = flag(data, "enabled", true, "growth.");
        stagePercent = array(data, "stage_percent", List.of(50D, 60D, 70D, 80D, 90D, 100D), 1, 100, true, true, "growth.");
        stageCost = array(data, "stage_cost", List.of(40D, 40D, 40D, 40D, 40D), .001, 1000000, false, false, "growth.");
        JsonObject e = group(data, "experience", "growth.");
        double min = integer(e, "min_percent", 90, 1, 1000, "growth.experience.");
        double max = integer(e, "max_percent", 110, 1, 1000, "growth.experience.");
        double cap = integer(e, "cap_percent", 125, 1, 1000, "growth.experience.");
        if (min > max || max > cap) {
            invalid("growth.experience.min_percent/max_percent/cap_percent", "require min <= max <= cap", "90/110/125");
            min = 90; max = 110; cap = 125;
        }
        experienceMinPercent = min; experienceMaxPercent = max; experienceCapPercent = cap;
        experienceCost = number(e, "cost", 12, .001, 1000000, "growth.experience.");
        JsonObject combat = group(data, "combat", "growth.");
        damageUnit = number(combat, "damage_unit", 10, .001, 1000000, "growth.combat.");
        rewardUnit = number(combat, "reward_unit", .25, 0, 1000000, "growth.combat.");
        baseRewardCap = number(combat, "base_cap", 4, 0, 1000000, "growth.combat.");
        bonusDamageThreshold = number(combat, "bonus_threshold", 300, 0, 1000000, "growth.combat.");
        bonusReward = number(combat, "bonus", 4, 0, 1000000, "growth.combat.");
        JsonObject profiles = group(data, "profiles", "growth.");
        Map<String, EquipmentGrowthPolicy.Profile> values = new LinkedHashMap<>();
        for (String rank : TierConfigProjection.RANKS) {
            String path = "growth.profiles." + rank + ".";
            JsonObject p = group(profiles, rank, "growth.profiles.");
            JsonObject d = UnifiedConfig.object(UnifiedConfig.object(defaults, "profiles"), rank);
            int start = integer(p, "start_day", d.get("start_day").getAsInt(), 1, 1000000, path);
            int duration = integer(p, "duration_days", d.get("duration_days").getAsInt(), 1, 1000000, path);
            List<Double> nodes = array(p, "progress_nodes", List.of(.2D, .4D, .6D, .8D), Math.nextUp(0D), Math.nextDown(1D), true, false, path);
            values.put(rank, new EquipmentGrowthPolicy.Profile(start, start + duration, nodes,
                    number(p, "early_baseline_gear_chance", d.get("early_baseline_gear_chance").getAsDouble(), 0, 1, path),
                    number(p, "early_gun_chance_multiplier", d.get("early_gun_chance_multiplier").getAsDouble(), 0, 1, path),
                    number(p, "early_enchantment_keep_chance", d.get("early_enchantment_keep_chance").getAsDouble(), 0, 1, path),
                    number(p, "early_enchantment_level_multiplier", d.get("early_enchantment_level_multiplier").getAsDouble(), 0, 1, path),
                    number(p, "early_totem_keep_chance", d.get("early_totem_keep_chance").getAsDouble(), 0, 1, path),
                    number(p, "early_trident_keep_chance", d.get("early_trident_keep_chance").getAsDouble(), 0, 1, path),
                    text(p, "early_spartan_material", d.get("early_spartan_material").getAsString(), path, "[a-z0-9_]+"),
                    items(p, d, "early_melee", path), items(p, d, "early_armor", path)));
        }
        equipmentGrowth = new EquipmentGrowthPolicy(enabled, Map.copyOf(values));
    }

    static GrowthConfig parse(JsonObject data, JsonObject defaults) { return new GrowthConfig(data, defaults); }
    public boolean enabled() { return enabled; }
    public double stagePercent(int level) { return stagePercent.get(Math.max(0, Math.min(5, level))); }
    public double stageCost(int level) {
        if (level < 0 || level >= 5) throw new IllegalArgumentException("Stage cost requires level 0..4: " + level);
        return stageCost.get(level);
    }
    public double experienceMinPercent() { return experienceMinPercent; }
    public double experienceMaxPercent() { return experienceMaxPercent; }
    public double experienceCapPercent() { return experienceCapPercent; }
    public double experienceCost() { return experienceCost; }
    public double damageUnit() { return damageUnit; }
    public double rewardUnit() { return rewardUnit; }
    public double baseRewardCap() { return baseRewardCap; }
    public double bonusDamageThreshold() { return bonusDamageThreshold; }
    public double bonusReward() { return bonusReward; }
    public int stageAt(String rank, long day) {
        EquipmentGrowthPolicy.Profile profile = equipmentGrowth.tiers().get(rank);
        if (profile == null) throw new IllegalArgumentException("Unknown growth rank: " + rank);
        return Math.max(0, Math.min(5, (int) Math.floor(profile.progress(day) * 5)));
    }
    EquipmentGrowthPolicy equipmentGrowth() { return equipmentGrowth; }

    static double number(JsonObject data, String key, double fallback, double min, double max, String path) {
        if (!data.has(key)) return fallback;
        try {
            JsonElement value = data.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            double result = value.getAsDouble();
            if (!Double.isFinite(result) || result < min || result > max) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException bad) {
            invalid(path + key, "finite number in [" + min + ", " + max + "]", fallback); return fallback;
        }
    }
    private static int integer(JsonObject data, String key, int fallback, int min, int max, String path) {
        double value = number(data, key, fallback, min, max, path);
        if (value != Math.rint(value)) { invalid(path + key, "integer", fallback); return fallback; }
        return (int) value;
    }
    private static boolean flag(JsonObject data, String key, boolean fallback, String path) {
        if (!data.has(key)) return fallback;
        JsonElement value = data.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
        invalid(path + key, "boolean", fallback); return fallback;
    }
    static JsonObject group(JsonObject data, String key, String path) {
        if (!data.has(key)) return new JsonObject();
        JsonElement value = data.get(key);
        if (value.isJsonObject()) return value.getAsJsonObject();
        invalid(path + key, "object", "default settings"); return new JsonObject();
    }
    private static List<Double> array(JsonObject data, String key, List<Double> fallback,
                                      double min, double max, boolean increasing, boolean integral, String path) {
        if (!data.has(key)) return fallback;
        try {
            JsonArray values = data.getAsJsonArray(key);
            if (values.size() != fallback.size()) throw new IllegalArgumentException();
            List<Double> result = new ArrayList<>();
            double previous = Double.NEGATIVE_INFINITY;
            for (JsonElement entry : values) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                double value = entry.getAsDouble();
                if (!Double.isFinite(value) || value < min || value > max || (increasing && value <= previous)
                        || (integral && value != Math.rint(value))) throw new IllegalArgumentException();
                result.add(value); previous = value;
            }
            return List.copyOf(result);
        } catch (RuntimeException bad) {
            invalid(path + key, fallback.size() + " numbers in [" + min + ", " + max + "]"
                    + (increasing ? ", strictly increasing" : "") + (integral ? ", integers" : ""), fallback);
            return fallback;
        }
    }
    private static String text(JsonObject data, String key, String fallback, String path, String pattern) {
        if (!data.has(key)) return fallback;
        JsonElement value = data.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().matches(pattern)) return value.getAsString();
        invalid(path + key, pattern, fallback); return fallback;
    }
    private static Map<String, String> items(JsonObject data, JsonObject defaults, String key, String path) {
        JsonObject values = group(data, key, path), d = UnifiedConfig.object(defaults, key);
        Map<String, String> result = new LinkedHashMap<>();
        for (String slot : d.keySet()) if (!slot.startsWith("_"))
            result.put(slot, text(values, slot, d.get(slot).getAsString(), path + key + ".", "[a-z0-9_.-]+:[a-z0-9_./-]+"));
        return Map.copyOf(result);
    }
    private static void invalid(String path, String expected, Object fallback) {
        String file = "global.json";
        if (path.startsWith("tiers.")) {
            int dot = path.indexOf('.', 6);
            file = path.substring(6, dot) + ".json";
            path = path.substring(dot + 1);
        }
        LogUtils.getLogger().warn("Invalid human configuration {}:{}; expected {}; using safe default {}", file, path, expected, fallback);
    }
}
