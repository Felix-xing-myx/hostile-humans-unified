package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Current-schema fallback only. Never translates old names or changes explicit values. */
final class CurrentConfigDefaults {
    private CurrentConfigDefaults() { }

    static boolean userMap(String path) {
        return path.matches("recruitment\\.payment_by_tier\\.(roamer|tier1|tier2|tier3)")
                || path.equals("tacz.gun_whitelist") || path.equals("tacz.hired_gun_additional_whitelist")
                || path.matches("tacz\\.tier_gun_type_weights\\.(roamer|tier1|tier2|tier3)");
    }

    static void fill(JsonObject data, JsonObject defaults, String path) {
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey(), child = path.isEmpty() ? key : path + "." + key;
            if (!data.has(key)) data.add(key, entry.getValue().deepCopy());
            else if (!userMap(child) && data.get(key).isJsonObject() && entry.getValue().isJsonObject())
                fill(data.getAsJsonObject(key), entry.getValue().getAsJsonObject(), child);
        }
    }
}
