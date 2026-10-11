package club.someoneice.humangunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Refresh only exact shipped lists. Custom and explicitly empty pools remain authoritative. */
final class TacticalSupplyDefaults {
    private TacticalSupplyDefaults() { }

    static void refresh(JsonObject data, JsonObject defaults, boolean rank) {
        if (rank) {
            JsonObject current = UnifiedConfig.object(UnifiedConfig.object(data, "potions"), "tactical");
            JsonObject template = UnifiedConfig.object(UnifiedConfig.object(defaults, "potions"), "tactical");
            if (!template.has("pool") || !template.get("pool").isJsonArray()) return;
            JsonArray old = new JsonArray();
            JsonArray previous = new JsonArray();
            JsonArray previousMolotov = template.getAsJsonArray("pool").deepCopy();
            for (var entry : previousMolotov) {
                if (!entry.isJsonObject()) continue;
                JsonObject row = entry.getAsJsonObject();
                if (row.has("item") && row.get("item").isJsonPrimitive()
                        && row.get("item").getAsString().equals("scguns:gas_grenade"))
                    row.addProperty("item", "scguns:molotov_cocktail");
            }
            for (var entry : template.getAsJsonArray("pool")) {
                if (entry.isJsonObject() && entry.getAsJsonObject().has("potion")) old.add(entry.deepCopy());
                if (!entry.isJsonObject()) continue;
                JsonObject row = entry.getAsJsonObject();
                if (row.has("potion") || (row.has("item") && row.get("item").isJsonPrimitive()
                        && List.of("scguns:grenade", "scguns:nail_bomb").contains(row.get("item").getAsString())))
                    previous.add(entry.deepCopy());
            }
            removeStun(current, "pool", true);
            if (current.has("pool") && (current.get("pool").equals(old) || current.get("pool").equals(previous)
                    || current.get("pool").equals(previousMolotov)))
                current.add("pool", template.get("pool").deepCopy());
        } else {
            JsonObject current = UnifiedConfig.object(UnifiedConfig.object(data, "scguns"), "throwables");
            JsonObject template = UnifiedConfig.object(UnifiedConfig.object(defaults, "scguns"), "throwables");
            JsonArray old = new JsonArray();
            for (String id : List.of("scguns:grenade", "scguns:stun_grenade", "scguns:nail_bomb")) old.add(id);
            if (current.has("allowed_items") && current.get("allowed_items").equals(old)
                    && template.has("allowed_items"))
                current.add("allowed_items", template.get("allowed_items").deepCopy());
            removeStun(current, "allowed_items", false);
            if (!current.has("initial_speed") && template.has("initial_speed"))
                current.add("initial_speed", template.get("initial_speed").deepCopy());
        }
    }
    /** Explicit feature removal; preserve every other custom entry and weight. */
    private static void removeStun(JsonObject object, String key, boolean pool) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return;
        JsonArray array = object.getAsJsonArray(key);
        for (int index = array.size() - 1; index >= 0; index--) {
            var entry = array.get(index);
            var id = pool && entry.isJsonObject() ? entry.getAsJsonObject().get("item") : entry;
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()
                    && id.getAsString().equals("scguns:stun_grenade")) array.remove(index);
        }
    }
}
