package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** NPC effects and inventory values; native First Aid timing remains API-owned. */
public record FirstAidSettings(boolean enabled, float plasterHealing, float bandageHealing,
                               int morphineTicks, int resistanceAmplifier,
                               double plasterQuality, double bandageQuality, double morphineQuality) {
    static FirstAidSettings parse(JsonObject data) {
        JsonObject quality = UnifiedConfig.object(data, "quality");
        return new FirstAidSettings(UnifiedConfig.bool(data, "enabled", true),
                (float) UnifiedConfig.number(data, "plaster_healing", 20, 0, 2048),
                (float) UnifiedConfig.number(data, "bandage_healing", 40, 0, 2048),
                (int) UnifiedConfig.number(data, "morphine_duration_seconds", 90, 1, 3600) * 20,
                (int) UnifiedConfig.number(data, "morphine_resistance_level", 1, 1, 5) - 1,
                UnifiedConfig.number(quality, "plaster", 60, 0, 1000),
                UnifiedConfig.number(quality, "bandage", 65, 0, 1000),
                UnifiedConfig.number(quality, "morphine", 94, 0, 1000));
    }
}
