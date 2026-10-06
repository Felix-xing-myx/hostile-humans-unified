package club.someoneice.humangunner;

import com.google.gson.*;
import java.util.List;

/** Lossless storage projection; gameplay continues to consume the established root shape. */
final class TierConfigProjection {
    static final List<String> RANKS = List.of("roamer", "tier1", "tier2", "tier3");
    private TierConfigProjection() { }

    static JsonObject global(JsonObject root) {
        JsonObject result = root.deepCopy();
        result.addProperty("schema_version", 2);
        // Keep group-level notes and unknown ranks/options, including custom guidance.
        for (String group : List.of("tiers", "equipment_loadouts", "tier_ai", "_tier_file_metadata")) removeRanks(result, group);
        removeRanks(object(result, "equipment_growth"), "tiers");
        JsonObject ai = object(result, "ai");
        JsonObject globalAi = new JsonObject();
        globalAi.addProperty("_说明_中文", "增强战斗AI总开关；具体撤退、恢复、盾牌与战术参数在四阶文件的ai中独立设置。");
        globalAi.addProperty("_description_en", "Enhanced combat AI master switch. Configure retreat, recovery, shields and tactics in each rank file's ai section.");
        if (ai.has("enabled")) globalAi.add("enabled", ai.get("enabled").deepCopy());
        result.add("ai", globalAi);
        JsonObject tacz = object(result, "tacz");
        for (String rank : RANKS) tacz.remove(rank + "_gun_chance");
        removeRanks(tacz, "tier_damage_multipliers");
        removeRanks(tacz, "tier_gun_type_weights");
        JsonObject recruitment = object(result, "recruitment");
        removeRanks(recruitment, "max_hired_by_tier");
        removeRanks(recruitment, "payment_by_tier");
        removeRanks(object(object(result, "spawning"), "progression"), "first_spawn_day_by_tier");
        // These groups have no global options after their four ranks move out.
        // Remove only shipped guidance, retaining unknown user extensions/notes.
        JsonObject defaults = UnifiedConfig.defaults();
        JsonObject progression = object(object(result, "spawning"), "progression");
        removeShippedGuidance(object(progression, "first_spawn_day_by_tier"),
                object(object(object(defaults, "spawning"), "progression"), "first_spawn_day_by_tier"), List.of());
        if (progression.has("first_spawn_day_by_tier") && progression.get("first_spawn_day_by_tier").isJsonObject()
                && object(progression, "first_spawn_day_by_tier").size() == 0)
            progression.remove("first_spawn_day_by_tier");
        for (String group : List.of("tiers", "equipment_loadouts", "tier_ai", "_tier_file_metadata")) {
            removeShippedGuidance(object(result, group), object(defaults, group), List.of());
            if (result.has(group) && result.get(group).isJsonObject() && object(result, group).size() == 0) result.remove(group);
        }
        removeShippedGuidance(object(result, "equipment_growth"), object(defaults, "equipment_growth"),
                List.of("_说明_中文", "_description_en", "_说明_enabled_中文", "_description_enabled_en"));
        for (String key : List.of("_说明_枪械分布_中文", "_description_gun_type_weights_en")) tacz.remove(key);
        return arrange(result, false);
    }

    static JsonObject tier(JsonObject root, String rank) {
        JsonObject result = object(object(root, "tiers"), rank).deepCopy();
        copyGuidance(object(root, "tiers"), result);
        for (String key : List.of("_说明_伤害层级_中文", "_description_damage_layers_en")) {
            JsonElement damage = result.get("damage_multipliers");
            if (damage != null && damage.isJsonObject()) {
                JsonElement guidance = result.remove(key);
                if (guidance != null && !damage.getAsJsonObject().has(key)) damage.getAsJsonObject().add(key, guidance);
            }
        }
        result.addProperty("schema_version", 2);
        copyAt(root, "equipment_loadouts." + rank, result, "equipment_loadout");
        copyAt(root, "equipment_growth.tiers." + rank, result, "equipment_growth");
        if (result.has("equipment_loadout") && result.get("equipment_loadout").isJsonObject())
            copyGuidance(object(root, "equipment_loadouts"), result.getAsJsonObject("equipment_loadout"));
        if (result.has("equipment_growth") && result.get("equipment_growth").isJsonObject())
            copyGuidance(object(root, "equipment_growth"), result.getAsJsonObject("equipment_growth"));
        JsonObject spawning = ensure(result, "spawning");
        copyAt(root, "spawning.progression.first_spawn_day_by_tier." + rank, spawning, "first_spawn_day");
        JsonObject metadata = object(object(root, "_tier_file_metadata"), rank);
        JsonObject tacz = object(metadata, "tacz").deepCopy();
        for (String key : List.of("_说明_枪械分布_中文", "_description_gun_type_weights_en"))
            copyAt(root, "tacz." + key, tacz, key);
        copyAt(root, "tacz." + rank + "_gun_chance", tacz, "gun_chance");
        copyAt(root, "tacz.tier_damage_multipliers." + rank, tacz, "damage_multiplier");
        copyAt(root, "tacz.tier_gun_type_weights." + rank, tacz, "gun_type_weights");
        result.add("tacz", tacz);
        JsonObject recruitment = object(metadata, "recruitment").deepCopy();
        copyAt(root, "recruitment.max_hired_by_tier." + rank, recruitment, "max_hired");
        JsonElement recruitmentRoot = root.get("recruitment");
        JsonElement payments = at(root, "recruitment.payment_by_tier");
        if ((recruitmentRoot != null && !recruitmentRoot.isJsonObject())
                || (payments != null && !payments.isJsonObject())) recruitment.add("payment", JsonNull.INSTANCE);
        else copyAt(root, "recruitment.payment_by_tier." + rank, recruitment, "payment");
        result.add("recruitment", recruitment);
        JsonElement tierAi = at(root, "tier_ai." + rank);
        JsonObject ai = tierAi != null && tierAi.isJsonObject() ? tierAi.getAsJsonObject().deepCopy()
                : object(root, "ai").deepCopy();
        ai.remove("enabled");
        result.add("ai", ai);
        return arrange(result, true);
    }

    static JsonObject arrange(JsonObject result, boolean rank) {
        return ordered(result, rank ? List.of("schema_version", "_说明_中文", "_description_en",
                "attributes", "movement", "damage_multipliers", "combat", "weapon_spread_degrees",
                "ai", "spawning", "equipment_loadout", "equipment_growth", "ammunition", "potions",
                "tacz", "recruitment") : List.of("schema_version", "_说明_中文", "_description_en",
                "vanilla_mode", "ai", "ammunition", "equipment_integration", "better_combat", "damage", "spawning", "equipment_growth",
                "recruitment", "tacz", "native_gameplay"));
    }

    static void applyTier(JsonObject root, String rank, JsonObject file) {
        JsonObject tier = file.deepCopy();
        tier.remove("schema_version");
        for (String key : List.of("equipment_loadout", "equipment_growth", "tacz", "recruitment", "ai")) tier.remove(key);
        JsonObject spawning = object(tier, "spawning");
        if (spawning.has("first_spawn_day")) {
            put(root, "spawning.progression.first_spawn_day_by_tier." + rank, spawning.remove("first_spawn_day"));
        }
        put(root, "tiers." + rank, tier);
        copyTo(file, "equipment_loadout", root, "equipment_loadouts." + rank);
        copyTo(file, "equipment_growth", root, "equipment_growth.tiers." + rank);
        copyTo(file, "tacz.gun_chance", root, "tacz." + rank + "_gun_chance");
        copyTo(file, "tacz.damage_multiplier", root, "tacz.tier_damage_multipliers." + rank);
        copyTo(file, "tacz.gun_type_weights", root, "tacz.tier_gun_type_weights." + rank);
        copyTo(file, "recruitment.max_hired", root, "recruitment.max_hired_by_tier." + rank);
        JsonElement recruitment = file.get("recruitment");
        if (recruitment != null && !recruitment.isJsonObject()) put(root, "recruitment.payment_by_tier." + rank, JsonNull.INSTANCE);
        else copyTo(file, "recruitment.payment", root, "recruitment.payment_by_tier." + rank);
        JsonObject ai = object(file, "ai").deepCopy();
        ai.remove("enabled");
        put(root, "tier_ai." + rank, ai);
        // Unknown metadata in the new grouped areas remains available for future readers.
        for (String group : List.of("tacz", "recruitment")) {
            JsonObject extras = object(file, group).deepCopy();
            for (String key : group.equals("tacz") ? List.of("gun_chance", "damage_multiplier", "gun_type_weights")
                    : List.of("max_hired", "payment")) extras.remove(key);
            if (extras.size() != 0) put(root, "_tier_file_metadata." + rank + "." + group, extras);
        }
    }

    /** Add only absent options. User-authored collections are complete replacements. */
    static void fill(JsonObject data, JsonObject defaults, String path) {
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey(), child = path.isEmpty() ? key : path + "." + key;
            if (!data.has(key)) data.add(key, entry.getValue().deepCopy());
            else if (!child.equals("recruitment.payment") && !child.equals("tacz.gun_type_weights") && !child.equals("tacz.gun_whitelist")
                    && !child.equals("tacz.hired_gun_additional_whitelist")
                    && data.get(key).isJsonObject() && entry.getValue().isJsonObject())
                fill(data.getAsJsonObject(key), entry.getValue().getAsJsonObject(), child);
        }
    }

    static JsonElement at(JsonObject root, String path) {
        JsonElement current = root;
        for (String key : path.split("\\.")) {
            if (!current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
            if (current == null) return null;
        }
        return current;
    }
    static void put(JsonObject root, String path, JsonElement value) {
        String[] keys = path.split("\\.");
        JsonObject parent = root;
        for (int i = 0; i < keys.length - 1; i++) parent = ensure(parent, keys[i]);
        parent.add(keys[keys.length - 1], value.deepCopy());
    }
    private static void copyTo(JsonObject source, String from, JsonObject target, String to) {
        JsonElement value = at(source, from);
        if (value != null) put(target, to, value);
    }
    private static void copyAt(JsonObject source, String from, JsonObject target, String key) {
        JsonElement value = at(source, from);
        if (value != null) target.add(key, value.deepCopy());
    }
    private static JsonObject object(JsonObject root, String key) { return UnifiedConfig.object(root, key); }
    private static boolean guidance(String key) {
        return key.startsWith("_说明_") || key.startsWith("_description_");
    }
    private static void copyGuidance(JsonObject source, JsonObject target) {
        for (var entry : source.entrySet()) {
            if (guidance(entry.getKey()) && !target.has(entry.getKey()))
                target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }
    private static void removeShippedGuidance(JsonObject target, JsonObject defaults, List<String> keep) {
        for (String key : defaults.keySet()) {
            if (guidance(key) && !keep.contains(key) && target.has(key)
                    && target.get(key).equals(defaults.get(key))) target.remove(key);
        }
    }
    private static JsonObject ordered(JsonObject source, List<String> keys) {
        JsonObject result = new JsonObject();
        for (String key : keys) if (source.has(key)) result.add(key, source.get(key));
        for (var entry : source.entrySet()) if (!result.has(entry.getKey())) result.add(entry.getKey(), entry.getValue());
        return adjacentGuidance(result);
    }
    /** Keep each field's bilingual help directly above the value, including projected fields. */
    private static JsonObject adjacentGuidance(JsonObject source) {
        JsonObject result = new JsonObject();
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            if (guidance(key)) {
                String field = key.startsWith("_说明_") && key.endsWith("_中文") && key.length() > 7
                        ? key.substring(4, key.length() - 3)
                        : key.startsWith("_description_") && key.endsWith("_en") && key.length() > 16
                        ? key.substring(13, key.length() - 3) : "";
                if (!field.isEmpty() && source.has(field)) continue;
            } else {
                for (String note : List.of("_说明_" + key + "_中文", "_description_" + key + "_en"))
                    if (source.has(note)) result.add(note, source.get(note));
            }
            JsonElement value = entry.getValue();
            result.add(key, value.isJsonObject() ? adjacentGuidance(value.getAsJsonObject()) : value);
        }
        return result;
    }
    private static JsonObject ensure(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key, new JsonObject());
        return root.getAsJsonObject(key);
    }
    private static void removeRanks(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        JsonObject group = root.getAsJsonObject(key);
        for (String rank : RANKS) group.remove(rank);
        if (group.size() == 0) root.remove(key);
    }
}
