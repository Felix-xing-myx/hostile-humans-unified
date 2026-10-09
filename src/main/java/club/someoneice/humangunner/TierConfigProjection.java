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

    /** Common controls first; remove retired controls without retaining runtime aliases. */
    static JsonObject arrange(JsonObject source, boolean rank) {
        JsonObject result = source.deepCopy();
        removeRetiredControls(result, rank);
        List<String> sections = rank
                ? List.of("schema_version", "_说明_中文", "_description_en", "attributes", "spawning",
                    "equipment_loadout", "ammunition", "food", "potions", "recruitment", "damage_multipliers",
                    "combat", "ai", "movement", "weapon_spread_degrees", "tacz", "equipment_growth")
                : List.of("schema_version", "_说明_中文", "_description_en", "vanilla_mode", "ammunition",
                    "equipment_integration", "ai", "spawning", "recruitment", "damage", "tacz",
                    "better_combat", "equipment_growth", "native_gameplay");
        orderGroup(result, "equipment_loadout", List.of("ranged_chance", "ranged_in_backpack", "allow_tacz_firearms",
                "automatic_spartan_weapons", "automatic_spartan_shields", "automatic_immersive_armors",
                "weapon_count", "guaranteed_shields_min", "guaranteed_shields_max", "automatic_shield_enchantments",
                "mainhand", "ranged_mainhand", "armor_sets", "shield_pool", "inventory", "offhand", "bonus_mainhand", "rules"));
        orderGroup(result, "attributes", List.of("health_min", "health_max", "attack_damage", "armor",
                "armor_toughness", "knockback_resistance", "follow_range"));
        orderGroup(result, "spawning", rank ? List.of("spawn_multiplier", "first_spawn_day")
                : List.of("enabled", "admission_chance", "battle_admission_chance", "progression",
                    "encounter_cooldown_ticks", "nearby_horizontal_radius", "nearby_vertical_radius", "density_divisor"));
        orderGroup(result, "recruitment", rank ? List.of("max_hired", "payment")
                : List.of("allow_hired_pvp_damage", "soldier_messages"));
        orderGroup(result, "ai", List.of("enabled", "item_recovery_enabled", "retreat_health_ratio", "low_health_combat", "critical_health_ratio",
                "resume_health_ratio", "gradual_heal_interval_ticks", "food_recovery_min_health", "food_recovery_max_health",
                "recovery_damage_tolerance_ratio", "recovery_commit_ticks", "shield_block_chance_after_hit",
                "shield_block_duration_min_ticks", "shield_block_duration_max_ticks", "shield_block_cooldown_ticks"));
        orderGroup(result, "tacz", rank ? List.of("gun_chance", "damage_multiplier", "gun_type_weights")
                : List.of("enabled", "natural_spawn_firearms_enabled", "auto_add_new_guns", "auto_added_gun_weight",
                    "excluded_auto_gun_types", "excluded_gun_namespaces", "gun_blacklist", "human_gun_damage_multiplier",
                    "pillager_gun_damage_multiplier", "shield_damage_multiplier", "tamed_maid_damage_multiplier",
                    "gunner_spread_degrees", "forced_ranged_gun_chance", "gun_drop_chance", "gun_whitelist",
                    "hired_gun_additional_whitelist"));
        return ordered(result, sections);
    }

    private static void removeRetiredControls(JsonObject data, boolean rank) {
        JsonObject damage = object(data, rank ? "damage_multipliers" : "damage");
        List<String> retired = rank
                ? List.of("bow_damage_multiplier", "trident_damage_multiplier", "incoming_damage_multiplier")
                : List.of("human_melee_damage_multiplier", "human_bow_damage_multiplier",
                    "human_trident_damage_multiplier", "bow_velocity_damage_multiplier",
                    "trident_velocity_damage_multiplier", "tamed_maid_melee_damage_multiplier");
        for (String key : retired) {
            damage.remove(key);
            damage.remove("_说明_" + key + "_中文");
            damage.remove("_description_" + key + "_en");
        }
        // Retired names inside shipped formula comments are no longer valid help.
        for (String key : List.of("_说明_中文", "_description_en", "_说明_近战计算_中文",
                "_description_melee_formula_en", "_说明_弹射物计算_中文", "_description_projectile_formula_en",
                "_说明_特殊倍率_中文", "_description_special_factors_en")) {
            JsonElement value = damage.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && retired.stream().anyMatch(value.getAsString()::contains)) damage.remove(key);
        }
        if (!rank) {
            JsonObject nativeGameplay = object(data, "native_gameplay");
            for (String key : List.of("no_waystones", "_说明_no_waystones_中文", "_description_no_waystones_en")) nativeGameplay.remove(key);
            for (String key : List.of("_说明_中文", "_description_en")) {
                JsonElement note = nativeGameplay.get(key);
                if (note != null && note.isJsonPrimitive() && note.getAsJsonPrimitive().isString()
                        && (note.getAsString().contains("no_waystones") || note.getAsString().contains("waystone")))
                    nativeGameplay.remove(key);
            }
        }
        if (rank) {
            JsonObject potions = object(data, "potions");
            for (String key : List.of("bonus", "_说明_bonus_中文", "_description_bonus_en")) potions.remove(key);
        }
    }

    private static void orderGroup(JsonObject parent, String key, List<String> fields) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return;
        java.util.ArrayList<String> keys = new java.util.ArrayList<>(List.of("_说明_中文", "_description_en"));
        keys.addAll(fields);
        parent.add(key, ordered(parent.getAsJsonObject(key), keys));
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
