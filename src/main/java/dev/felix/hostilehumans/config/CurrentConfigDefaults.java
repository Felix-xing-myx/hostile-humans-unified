package dev.felix.hostilehumans.config;

import dev.felix.hostilehumans.equipment.SpawnSupplyMigration;

import com.google.gson.JsonObject;

/** Fill absent controls only, after raw supply migration; explicit user maps and pools remain authoritative. */
public final class CurrentConfigDefaults {
    private CurrentConfigDefaults() { }

    static boolean userMap(String path) {
        return path.equals("faction_abilities.assignments") || path.endsWith("food.enchanted_golden_apple_counts") || path.endsWith("_archetype_weights")
                || path.endsWith("equipment_archetypes") || path.endsWith("faction_armor")
                || path.equals("scguns.medical_supplies") || path.matches("recruitment\\.payment_by_tier\\.(roamer|tier1|tier2|tier3)")
                || path.endsWith(".gun_whitelist")
                || path.matches("tacz\\.tier_gun_type_weights\\.(roamer|tier1|tier2|tier3)");
    }

    public static void fill(JsonObject data, JsonObject defaults, String path) {
        if(path.isEmpty()) SpawnSupplyMigration.migrateRoot(data);
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey(), child = path.isEmpty() ? key : path + "." + key;
            if (!data.has(key)) data.add(key, entry.getValue().deepCopy());
            else if (!userMap(child) && data.get(key).isJsonObject() && entry.getValue().isJsonObject())
                fill(data.getAsJsonObject(key), entry.getValue().getAsJsonObject(), child);
        }
    }
}
