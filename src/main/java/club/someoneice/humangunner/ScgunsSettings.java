package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable server configuration. No optional API types cross this boundary. */
public record ScgunsSettings(boolean enabled, double accuracyModifier, double damageMultiplier,
        boolean throwablesEnabled, int throwCooldownTicks, double throwMinRange, double throwMaxRange,
        double friendlySafetyRadius, int fuseTicks, double throwableQuality, java.util.Set<String> allowedThrowables,
        boolean flareEnabled, String raidId, int flareCooldownTicks, Map<String, Medicine> medicines, Map<String, Integer> magazineCosts) {
    public record Medicine(float healing, int useTicks, int cooldownTicks, double quality, List<Effect> effects) {}
    public record Effect(ResourceLocation id, int durationTicks, int amplifier) {}
    static ScgunsSettings parse(JsonObject data) {
        JsonObject throwing = UnifiedConfig.object(data, "throwables");
        JsonObject flare = UnifiedConfig.object(data, "signal_flare");
        Map<String, Medicine> medicines = new LinkedHashMap<>();
        for (var entry : UnifiedConfig.object(data, "medical_supplies").entrySet()) {
            if (!entry.getValue().isJsonObject() || entry.getKey().startsWith("_")) continue;
            JsonObject row = entry.getValue().getAsJsonObject();
            if (!UnifiedConfig.bool(row, "enabled", true)) continue;
            List<Effect> effects = new ArrayList<>();
            if (row.has("effects") && row.get("effects").isJsonArray()) for (var value : row.getAsJsonArray("effects")) {
                if (!value.isJsonObject()) continue;
                JsonObject effect = value.getAsJsonObject();
                if (!effect.has("id") || !effect.get("id").isJsonPrimitive()) continue;
                ResourceLocation id = ResourceLocation.tryParse(effect.get("id").getAsString());
                if (id != null) effects.add(new Effect(id,
                        (int) UnifiedConfig.number(effect, "duration_ticks", 100, 1, 72000),
                        (int) UnifiedConfig.number(effect, "level", 1, 1, 256) - 1));
            }
            if (ResourceLocation.tryParse(entry.getKey()) == null) continue;
            medicines.put(entry.getKey(), new Medicine((float) UnifiedConfig.number(row, "healing", 4, 0, 2048),
                    (int) UnifiedConfig.number(row, "use_ticks", 32, 1, 1200),
                    (int) UnifiedConfig.number(row, "shared_cooldown_ticks", 100, 1, 72000),
                    UnifiedConfig.number(row, "quality", 60, 0, 1000), List.copyOf(effects)));
        }
        Set<String> allowed = new LinkedHashSet<>();
        if (throwing.has("allowed_items") && throwing.get("allowed_items").isJsonArray())
            for (var id : throwing.getAsJsonArray("allowed_items"))
                if (id.isJsonPrimitive() && ResourceLocation.tryParse(id.getAsString()) != null) allowed.add(id.getAsString());
        Map<String, Integer> costs = new LinkedHashMap<>();
        JsonObject configuredCosts = UnifiedConfig.object(data, "magazines_per_reload_by_type");
        for (String type : List.of("pistol", "magnum", "smg", "rifle", "shotgun", "sniper", "lmg",
                "heavy", "flamethrower", "shock", "laser", "plasma", "special")) {
            int fallback = switch (type) { case "pistol", "magnum", "smg" -> 1; case "sniper" -> 3;
                case "lmg" -> 4; case "heavy", "flamethrower" -> 5; default -> 2; };
            costs.put(type, (int) UnifiedConfig.number(configuredCosts, type, fallback, 1, 64));
        }
        String raid = flare.has("raid_id") && flare.get("raid_id").isJsonPrimitive() ? flare.get("raid_id").getAsString() : "copper";
        double min = UnifiedConfig.number(throwing, "minimum_range", 6, 1, 32);
        return new ScgunsSettings(UnifiedConfig.bool(data, "enabled", true),
                UnifiedConfig.number(data, "ai_accuracy_modifier", 2, .05, 1000),
                UnifiedConfig.number(data, "npc_damage_multiplier", 1, 0, 100),
                UnifiedConfig.bool(throwing, "enabled", true),
                (int) UnifiedConfig.number(throwing, "cooldown_ticks", 200, 1, 72000), min,
                Math.max(min, UnifiedConfig.number(throwing, "maximum_range", 20, 1, 64)),
                UnifiedConfig.number(throwing, "friendly_safety_radius", 8, 1, 64),
                (int) UnifiedConfig.number(throwing, "fuse_ticks", 60, 20, 200),
                UnifiedConfig.number(throwing, "quality", 50, 0, 1000), Set.copyOf(allowed),
                UnifiedConfig.bool(flare, "enabled", true), raid,
                (int) UnifiedConfig.number(flare, "cooldown_ticks", 1200, 20, 72000), Map.copyOf(medicines), Map.copyOf(costs));
    }
}
