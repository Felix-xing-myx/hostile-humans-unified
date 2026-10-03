package club.someoneice.humangunner;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable, restart-scoped settings. No optional-mod API or world access. */
public final class UnifiedConfig {
    private static volatile UnifiedConfig instance;
    private final JsonObject root;
    private final Map<String, Tier> tiers;
    private final Set<String> blacklist;
    private final Spawn spawn;
    private final EquipmentGrowthPolicy equipmentGrowth;
    private final Map<String, ConfiguredEquipmentLoadout> equipmentLoadouts;
    private final Recruitment recruitment;
    private final boolean betterCombatSoldierMeleeEnabled;
    private final SoldierMessages soldierMessages;
    public record SoldierMessages(int chatterIntervalTicks, int chatterMaxPerFiveMinutes,
            int reportIntervalTicks, int reportMaxPerMinute, int reportDetailsPerMessage) { }

    public record Tier(int healthMin, int healthMax, double baseMovementSpeed,
            double attackDamage, double armor, double armorToughness,
            double knockbackResistance, double followRange, double meleeDamage, double bowDamage,
            double tridentDamage, double incomingDamage, double spawnMultiplier,
            Map<String, Double> firearmSpreadDegrees, Map<String, Double> projectileSpreadDegrees,
            int meleeCooldownMin, int meleeCooldownMax) {
        public double gunSpreadDegrees(String rawType) {
            return firearmSpreadDegrees.getOrDefault(
                    GunSpreadPolicy.category(rawType), firearmSpreadDegrees.getOrDefault("other", 0.0D));
        }

        public double projectileSpreadDegrees(String type) {
            return projectileSpreadDegrees.getOrDefault(type, projectileSpreadDegrees.getOrDefault("bow", 0.0D));
        }
    }
    public record Spawn(boolean enabled, double admissionChance, double battleChance,
            int cooldownTicks, int horizontalRadius, int verticalRadius, double densityDivisor,
            NaturalSpawnProgression progression) {}
    public record Recruitment(int roamerLimit, int tier1Limit, int tier2Limit, int tier3Limit,
            boolean allowHiredPvpDamage) {
        int limit(int tier) {
            return switch (tier) {
                case 0 -> roamerLimit;
                case 1 -> tier1Limit;
                case 2 -> tier2Limit;
                case 3 -> tier3Limit;
                default -> throw new IllegalArgumentException("Unknown recruitment tier: " + tier);
            };
        }
    }

    UnifiedConfig(JsonObject configured) {
        JsonObject defaults = defaults();
        root = merge(defaults, configured);
        Map<String, ConfiguredEquipmentLoadout> loadouts = new LinkedHashMap<>();
        for (String rank : List.of("roamer", "tier1", "tier2", "tier3")) {
            loadouts.put(rank, ConfiguredEquipmentLoadout.parse(
                    object(object(root, "equipment_loadouts"), rank), rank));
        }
        equipmentLoadouts = Map.copyOf(loadouts);
        Map<String, Tier> values = new LinkedHashMap<>();
        for (String key : List.of("roamer", "tier1", "tier2", "tier3")) {
            JsonObject t = object(object(root, "tiers"), key);
            JsonObject d = defaults.getAsJsonObject("tiers").getAsJsonObject(key);
            JsonObject defaultAttributes = object(d, "attributes");
            JsonObject defaultDamage = object(d, "damage_multipliers");
            JsonObject defaultCombat = object(d, "combat");
            JsonObject defaultSpawning = object(d, "spawning");
            int low = (int) number(object(t, "attributes"), "health_min",
                    defaultAttributes.get("health_min").getAsDouble(), 1, 1024);
            int high = (int) number(object(t, "attributes"), "health_max",
                    defaultAttributes.get("health_max").getAsDouble(), 1, 1024);
            int meleeCooldownLow = (int) number(object(t, "combat"), "melee_cooldown_min",
                    defaultCombat.get("melee_cooldown_min").getAsDouble(), 1, 200);
            int meleeCooldownHigh = (int) number(object(t, "combat"), "melee_cooldown_max",
                    defaultCombat.get("melee_cooldown_max").getAsDouble(), 1, 200);
            Map<String, Double> firearmSpreads = new LinkedHashMap<>();
            JsonObject defaultFirearms = object(object(d, "weapon_spread_degrees"), "firearms");
            for (String type : GunSpreadPolicy.supportedTypes()) {
                firearmSpreads.put(type, number(object(object(t, "weapon_spread_degrees"), "firearms"), type,
                        defaultFirearms.get(type).getAsDouble(), 0, 45));
            }
            Map<String, Double> projectileSpreads = new LinkedHashMap<>();
            JsonObject defaultProjectiles = object(object(d, "weapon_spread_degrees"), "projectiles");
            for (String type : List.of("bow", "crossbow", "trident")) {
                projectileSpreads.put(type, number(object(object(t, "weapon_spread_degrees"), "projectiles"), type,
                        defaultProjectiles.get(type).getAsDouble(), 0, 45));
            }
            values.put(key, new Tier(Math.min(low, high), Math.max(low, high),
                    number(object(t, "movement"), "base_speed",
                            number(object(d, "movement"), "base_speed", .105D, .01D, .2D), .01D, .2D),
                    number(object(t, "attributes"), "attack_damage",
                            defaultAttributes.get("attack_damage").getAsDouble(), 0, 2048),
                    number(object(t, "attributes"), "armor",
                            defaultAttributes.get("armor").getAsDouble(), 0, 30),
                    number(object(t, "attributes"), "armor_toughness",
                            defaultAttributes.get("armor_toughness").getAsDouble(), 0, 20),
                    number(object(t, "attributes"), "knockback_resistance",
                            defaultAttributes.get("knockback_resistance").getAsDouble(), 0, 1),
                    number(object(t, "attributes"), "follow_range",
                            defaultAttributes.get("follow_range").getAsDouble(), 8, 128),
                    number(object(t, "damage_multipliers"), "melee_damage_multiplier",
                            defaultDamage.get("melee_damage_multiplier").getAsDouble(), 0, 10),
                    number(object(t, "damage_multipliers"), "bow_damage_multiplier",
                            defaultDamage.get("bow_damage_multiplier").getAsDouble(), 0, 10),
                    number(object(t, "damage_multipliers"), "trident_damage_multiplier",
                            defaultDamage.get("trident_damage_multiplier").getAsDouble(), 0, 10),
                    number(object(t, "damage_multipliers"), "incoming_damage_multiplier",
                            defaultDamage.get("incoming_damage_multiplier").getAsDouble(), 0, 10),
                    number(object(t, "spawning"), "spawn_multiplier",
                            defaultSpawning.get("spawn_multiplier").getAsDouble(), 0, 100),
                    Map.copyOf(firearmSpreads), Map.copyOf(projectileSpreads),
                    Math.min(meleeCooldownLow, meleeCooldownHigh),
                    Math.max(meleeCooldownLow, meleeCooldownHigh)));
        }
        tiers = Map.copyOf(values);
        JsonObject s = object(root, "spawning");
        JsonObject progression = object(s, "progression");
        JsonObject firstDays = object(progression, "first_spawn_day_by_tier");
        spawn = new Spawn(bool(s, "enabled", true), number(s, "admission_chance", .08, 0, 1),
                number(s, "battle_admission_chance", .08, 0, 1),
                (int) number(s, "encounter_cooldown_ticks", 2400, 0, 72000),
                (int) number(s, "nearby_horizontal_radius", 48, 1, 96),
                (int) number(s, "nearby_vertical_radius", 16, 1, 64),
                number(s, "density_divisor", 4, .1, 1000),
                new NaturalSpawnProgression(bool(progression, "enabled", true),
                        (int) number(progression, "safe_days", 1, 0, 1000000),
                        (int) number(firstDays, "roamer", 3, 1, 1000000),
                        (int) number(firstDays, "tier1", 5, 1, 1000000),
                        (int) number(firstDays, "tier2", 10, 1, 1000000),
                        (int) number(firstDays, "tier3", 20, 1, 1000000)));
        JsonObject growth = object(root, "equipment_growth");
        Map<String, EquipmentGrowthPolicy.Profile> growthTiers = new LinkedHashMap<>();
        for (String key : List.of("roamer", "tier1", "tier2", "tier3")) {
            JsonObject g = object(object(growth, "tiers"), key);
            JsonObject d = object(object(object(defaults, "equipment_growth"), "tiers"), key);
            growthTiers.put(key, new EquipmentGrowthPolicy.Profile(
                    (int) number(g, "start_day", number(d, "start_day", 1, 1, 1000000), 1, 1000000),
                    (int) number(g, "full_quality_day", number(d, "full_quality_day", 80, 1, 1000000), 1, 1000000),
                    number(g, "early_baseline_gear_chance", 0, 0, 1),
                    number(g, "early_gun_chance_multiplier", .25, 0, 1),
                    number(g, "early_enchantment_keep_chance", .15, 0, 1),
                    number(g, "early_enchantment_level_multiplier", .25, 0, 1),
                    number(g, "early_totem_keep_chance", 0, 0, 1),
                    number(g, "early_trident_keep_chance", 0, 0, 1),
                    string(g, "early_spartan_material", string(d, "early_spartan_material", "wooden")),
                    growthItems(object(g, "early_melee"), object(d, "early_melee")),
                    growthItems(object(g, "early_armor"), object(d, "early_armor"))));
        }
        equipmentGrowth = new EquipmentGrowthPolicy(bool(growth, "enabled", true), Map.copyOf(growthTiers));
        JsonObject r = object(root, "recruitment");
        JsonObject limits = object(r, "max_hired_by_tier");
        recruitment = new Recruitment(
                (int) number(limits, "roamer", 12, 0, 10000),
                (int) number(limits, "tier1", 10, 0, 10000),
                (int) number(limits, "tier2", 6, 0, 10000),
                (int) number(limits, "tier3", 3, 0, 10000),
                bool(r, "allow_hired_pvp_damage", false));
        betterCombatSoldierMeleeEnabled = bool(object(root, "better_combat"), "soldier_melee_enabled", true);
        JsonObject messages = object(object(root, "recruitment"), "soldier_messages");
        soldierMessages = new SoldierMessages(
                (int) number(messages, "chatter_interval_ticks", 1200, 100, 72000),
                (int) number(messages, "chatter_max_per_five_minutes", 3, 0, 20),
                (int) number(messages, "report_interval_ticks", 100, 20, 1200),
                (int) number(messages, "report_max_per_minute", 6, 1, 20),
                (int) number(messages, "report_details_per_message", 3, 1, 5));
        Set<String> banned = new HashSet<>();
        JsonElement list = object(root, "tacz").get("gun_blacklist");
        if (list != null && list.isJsonArray()) {
            for (JsonElement item : list.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) banned.add(item.getAsString());
            }
        }
        blacklist = Set.copyOf(banned);
    }

    public static UnifiedConfig get() {
        UnifiedConfig value = instance;
        if (value == null) synchronized (UnifiedConfig.class) {
            if ((value = instance) == null) instance = value = load(FMLPaths.CONFIGDIR.get());
        }
        return value;
    }
    static synchronized UnifiedConfig reloadForServer() {
        return instance = load(FMLPaths.CONFIGDIR.get());
    }
    public Tier tier(String key) { return tiers.getOrDefault(key, tiers.get("roamer")); }
    public Spawn spawn() { return spawn; }
    EquipmentGrowthPolicy equipmentGrowth() { return equipmentGrowth; }
    public ConfiguredEquipmentLoadout equipmentLoadout(String rank) {
        return equipmentLoadouts.getOrDefault(rank, equipmentLoadouts.get("roamer"));
    }
    public int recruitmentLimit(int tier) { return recruitment.limit(tier); }
    public record RecruitmentCost(String itemId, int count) {}
    public RecruitmentCost recruitmentCost(int tier) {
        if (tier < 0 || tier > 3) throw new IllegalArgumentException("Unknown recruitment tier: " + tier);
        String key = List.of("roamer", "tier1", "tier2", "tier3").get(tier);
        JsonObject cost = object(object(object(root, "recruitment"), "payment_by_tier"), key);
        String id;
        int count;
        try {
            JsonElement item = cost.get("item");
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return null;
            id = item.getAsString();
            if (net.minecraft.resources.ResourceLocation.tryParse(id) == null || !id.contains(":")) return null;
            JsonElement amount = cost.get("count");
            if (amount == null || !amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) return null;
            count = amount.getAsBigDecimal().intValueExact();
            if (count < 0 || count > 1_000_000) return null;
        } catch (RuntimeException invalid) { return null; }
        return new RecruitmentCost(id, count);
    }
    public boolean allowHiredPvpDamage() { return recruitment.allowHiredPvpDamage(); }
    public SoldierMessages soldierMessages() { return soldierMessages; }
    public boolean betterCombatSoldierMeleeEnabled() { return betterCombatSoldierMeleeEnabled; }
    public boolean taczEnabled() { return bool(object(root, "tacz"), "enabled", true); }
    public boolean blacklisted(String id) { return blacklist.contains(id); }
    public double damage(String key, double fallback) { return number(object(root, "damage"), key, fallback, 0, 10); }
    public double gunSetting(String key, double fallback) { return number(object(root, "tacz"), key, fallback, 0, 10); }
    public double tierGunDamage(String key) {
        return number(object(object(root, "tacz"), "tier_damage_multipliers"), key, 1, 0, 10);
    }
    JsonObject ai() { return object(root, "ai").deepCopy(); }
    JsonObject gunConfig() {
        return object(root, "tacz").deepCopy();
    }

    boolean naturalFirearmsEnabled() {
        return bool(object(root, "tacz"), "natural_spawn_firearms_enabled", true);
    }

    static UnifiedConfig load(Path directory) {
        try {
            return new UnifiedConfig(SplitConfigFiles.load(directory));
        } catch (Exception error) {
            com.mojang.logging.LogUtils.getLogger().error("Could not load human configuration in {}; survival hiring disabled, other settings use defaults without overwriting input", directory, error);
            JsonObject fallback = new JsonObject();
            JsonObject recruitment = new JsonObject();
            recruitment.add("payment_by_tier", com.google.gson.JsonNull.INSTANCE);
            fallback.add("recruitment", recruitment);
            return new UnifiedConfig(fallback);
        }
    }

    static JsonObject migrate(JsonObject guns, JsonObject ai) {
        JsonObject data = defaults();
        JsonObject tacz = data.getAsJsonObject("tacz");
        // Normalize the aliases before merging with current defaults.
        alias(guns, "globe", "tier2_gun_chance");
        alias(guns, "guns", "gun_whitelist");
        alias(guns, "include_unlisted_guns", "auto_add_new_guns");
        alias(guns, "unlisted_gun_weight", "auto_added_gun_weight");
        alias(guns, "excluded_unlisted_gun_types", "excluded_auto_gun_types");
        for (var entry : guns.entrySet()) {
            if (entry.getKey().equals("human_melee_damage_multiplier")
                    || entry.getKey().equals("human_bow_damage_multiplier")
                    || entry.getKey().equals("human_trident_damage_multiplier"))
                data.getAsJsonObject("damage").add(entry.getKey(), entry.getValue().deepCopy());
            else tacz.add(entry.getKey(), entry.getValue().deepCopy());
        }
        for (var entry : ai.entrySet()) {
            // Deprecated speed fields belonged to the old path/combat/retreat
            // controller and do not map to the current base-speed formula.
            if (!Set.of("normal_movement_speed", "combat_movement_speed", "retreat_movement_speed")
                    .contains(entry.getKey())) {
                data.getAsJsonObject("ai").add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        if (ai.has("recovery_damage_tolerance") && !ai.has("recovery_damage_tolerance_ratio"))
            data.getAsJsonObject("ai").addProperty("recovery_damage_tolerance_ratio",
                    number(ai, "recovery_damage_tolerance", 10, 0, 60) / 60.0);
        return data;
    }
    private static void alias(JsonObject data, String old, String current) {
        if (!data.has(current) && data.has(old)) data.add(current, data.get(old).deepCopy());
        data.remove(old);
    }
    static JsonObject defaults() {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                UnifiedConfig.class.getResourceAsStream("/defaults/hostile_humans_unified.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Missing packaged config defaults", e); }
    }
    private static JsonObject merge(JsonObject defaults, JsonObject data) {
        JsonObject result = data.deepCopy();
        ConfigSchemaUpgrade.aliases(result, defaults);
        ConfigSchemaUpgrade.fill(result, defaults, "");
        return result;
    }
    private static Map<String, String> growthItems(JsonObject configured, JsonObject defaults) {
        Map<String, String> items = new LinkedHashMap<>();
        for (String key : defaults.keySet()) {
            if (!key.startsWith("_")) items.put(key, string(configured, key, defaults.get(key).getAsString()));
        }
        return Map.copyOf(items);
    }

    private static String string(JsonObject data, String key, String fallback) {
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : fallback;
    }

    static JsonObject object(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }
    static double number(JsonObject data, String key, double fallback, double min, double max) {
        try {
            double value = data.has(key) ? data.get(key).getAsDouble() : fallback;
            return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    static boolean bool(JsonObject data, String key, boolean fallback) {
        JsonElement value = data.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : fallback;
    }
}
