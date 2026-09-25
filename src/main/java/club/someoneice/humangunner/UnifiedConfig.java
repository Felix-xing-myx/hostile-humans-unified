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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final JsonObject root;
    private final Map<String, Tier> tiers;
    private final Set<String> blacklist;
    private final Spawn spawn;
    private final Recruitment recruitment;

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
    public record Spawn(boolean enabled, double admissionChance, double battleChance, int legacyRoll,
            int cooldownTicks, int horizontalRadius, int verticalRadius, double densityDivisor) {}
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
        Map<String, Tier> values = new LinkedHashMap<>();
        for (String key : List.of("roamer", "tier1", "tier2", "tier3")) {
            JsonObject t = object(object(root, "tiers"), key);
            JsonObject configuredTier = object(object(configured, "tiers"), key);
            JsonObject d = defaults.getAsJsonObject("tiers").getAsJsonObject(key);
            JsonObject defaultAttributes = object(d, "attributes");
            JsonObject defaultDamage = object(d, "damage_multipliers");
            JsonObject defaultCombat = object(d, "combat");
            JsonObject defaultSpawning = object(d, "spawning");
            int low = (int) tierNumber(configuredTier, t, "attributes", "health_min",
                    defaultAttributes.get("health_min").getAsDouble(), 1, 1024);
            int high = (int) tierNumber(configuredTier, t, "attributes", "health_max",
                    defaultAttributes.get("health_max").getAsDouble(), 1, 1024);
            int meleeCooldownLow = (int) tierNumber(configuredTier, t, "combat", "melee_cooldown_min",
                    defaultCombat.get("melee_cooldown_min").getAsDouble(), 1, 200);
            int meleeCooldownHigh = (int) tierNumber(configuredTier, t, "combat", "melee_cooldown_max",
                    defaultCombat.get("melee_cooldown_max").getAsDouble(), 1, 200);
            Map<String, Double> firearmSpreads = new LinkedHashMap<>();
            JsonObject defaultFirearms = object(object(d, "weapon_spread_degrees"), "firearms");
            for (String type : GunSpreadPolicy.supportedTypes()) {
                firearmSpreads.put(type, firearmSpread(configuredTier, t, type,
                        defaultFirearms.get(type).getAsDouble()));
            }
            Map<String, Double> projectileSpreads = new LinkedHashMap<>();
            JsonObject defaultProjectiles = object(object(d, "weapon_spread_degrees"), "projectiles");
            for (String type : List.of("bow", "crossbow", "trident")) {
                projectileSpreads.put(type, projectileSpread(configuredTier, t, type,
                        defaultProjectiles.get(type).getAsDouble()));
            }
            values.put(key, new Tier(Math.min(low, high), Math.max(low, high),
                    number(object(t, "movement"), "base_speed",
                            number(object(d, "movement"), "base_speed", .105D, .01D, .2D), .01D, .2D),
                    tierNumber(configuredTier, t, "attributes", "attack_damage",
                            defaultAttributes.get("attack_damage").getAsDouble(), 0, 2048),
                    tierNumber(configuredTier, t, "attributes", "armor",
                            defaultAttributes.get("armor").getAsDouble(), 0, 30),
                    tierNumber(configuredTier, t, "attributes", "armor_toughness",
                            defaultAttributes.get("armor_toughness").getAsDouble(), 0, 20),
                    tierNumber(configuredTier, t, "attributes", "knockback_resistance",
                            defaultAttributes.get("knockback_resistance").getAsDouble(), 0, 1),
                    tierNumber(configuredTier, t, "attributes", "follow_range",
                            defaultAttributes.get("follow_range").getAsDouble(), 8, 128),
                    tierNumber(configuredTier, t, "damage_multipliers", "melee_damage_multiplier",
                            defaultDamage.get("melee_damage_multiplier").getAsDouble(), 0, 10),
                    tierNumber(configuredTier, t, "damage_multipliers", "bow_damage_multiplier",
                            defaultDamage.get("bow_damage_multiplier").getAsDouble(), 0, 10),
                    tierNumber(configuredTier, t, "damage_multipliers", "trident_damage_multiplier",
                            defaultDamage.get("trident_damage_multiplier").getAsDouble(), 0, 10),
                    tierNumber(configuredTier, t, "damage_multipliers", "incoming_damage_multiplier",
                            defaultDamage.get("incoming_damage_multiplier").getAsDouble(), 0, 10),
                    tierNumber(configuredTier, t, "spawning", "spawn_multiplier",
                            defaultSpawning.get("spawn_multiplier").getAsDouble(), 0, 100),
                    Map.copyOf(firearmSpreads), Map.copyOf(projectileSpreads),
                    Math.min(meleeCooldownLow, meleeCooldownHigh),
                    Math.max(meleeCooldownLow, meleeCooldownHigh)));
        }
        tiers = Map.copyOf(values);
        JsonObject s = object(root, "spawning");
        spawn = new Spawn(bool(s, "enabled", true), number(s, "admission_chance", .08, 0, 1),
                number(s, "battle_admission_chance", .08, 0, 1),
                (int) number(s, "roamer_legacy_roll", 200, 1, 1000000),
                (int) number(s, "encounter_cooldown_ticks", 2400, 0, 72000),
                (int) number(s, "nearby_horizontal_radius", 48, 1, 96),
                (int) number(s, "nearby_vertical_radius", 16, 1, 64),
                number(s, "density_divisor", 4, .1, 1000));
        JsonObject r = object(root, "recruitment");
        JsonObject limits = object(r, "max_hired_by_tier");
        recruitment = new Recruitment(
                (int) number(limits, "roamer", 12, 0, 10000),
                (int) number(limits, "tier1", 10, 0, 10000),
                (int) number(limits, "tier2", 6, 0, 10000),
                (int) number(limits, "tier3", 3, 0, 10000),
                bool(r, "allow_hired_pvp_damage", false));
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
    public Tier tier(String key) { return tiers.getOrDefault(key, tiers.get("roamer")); }
    public Spawn spawn() { return spawn; }
    public int recruitmentLimit(int tier) { return recruitment.limit(tier); }
    public boolean allowHiredPvpDamage() { return recruitment.allowHiredPvpDamage(); }
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

    static UnifiedConfig load(Path directory) {
        Path path = directory.resolve("hostile_humans_unified.json");
        try {
            if (Files.exists(path)) {
                JsonObject data = read(path);
                if (data.has("schema_version") && data.get("schema_version").getAsInt() != 1)
                    throw new IOException("Unsupported config schema; file left unchanged");
                return new UnifiedConfig(data);
            }
            JsonObject migrated = migrate(readIfPresent(directory.resolve("humangunner.json")),
                    readIfPresent(directory.resolve("humangunner_ai.json")));
            UnifiedConfig result = new UnifiedConfig(migrated);
            Files.createDirectories(directory);
            // Write a complete sibling file, then rename without REPLACE_EXISTING.
            // A crash must not leave a half-written authoritative configuration.
            Path temporary = Files.createTempFile(directory, "hostile-humans-config-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(migrated, writer);
                }
                try { Files.move(temporary, path); }
                catch (FileAlreadyExistsException concurrentWriter) { return new UnifiedConfig(read(path)); }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return result;
        } catch (Exception error) {
            com.mojang.logging.LogUtils.getLogger().error("Could not load unified config {}; using safe defaults without overwriting input", path, error);
            return new UnifiedConfig(new JsonObject());
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
    private static JsonObject readIfPresent(Path path) throws IOException {
        return Files.exists(path) ? read(path) : new JsonObject();
    }
    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static JsonObject merge(JsonObject defaults, JsonObject data) {
        JsonObject result = defaults.deepCopy();
        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && result.has(key) && result.get(key).isJsonObject())
                result.add(key, merge(result.getAsJsonObject(key), value.getAsJsonObject()));
            else result.add(key, value.deepCopy());
        }
        return result;
    }
    private static double tierNumber(JsonObject configuredTier, JsonObject mergedTier,
            String group, String key, double fallback, double min, double max) {
        JsonObject configuredGroup = object(configuredTier, group);
        if (configuredGroup.has(key)) return number(configuredGroup, key, fallback, min, max);
        if (configuredTier.has(key)) return number(configuredTier, key, fallback, min, max);
        JsonObject mergedGroup = object(mergedTier, group);
        if (mergedGroup.has(key)) return number(mergedGroup, key, fallback, min, max);
        return number(mergedTier, key, fallback, min, max);
    }

    private static double firearmSpread(JsonObject configuredTier, JsonObject mergedTier,
            String type, double fallback) {
        JsonObject configuredWeaponSpreads = object(configuredTier, "weapon_spread_degrees");
        JsonObject configuredFirearmGroup = object(configuredWeaponSpreads, "firearms");
        if (configuredFirearmGroup.has(type)) return number(configuredFirearmGroup, type, fallback, 0, 45);
        if (configuredTier.has("gun_spread_degrees")) {
            double legacyBase = number(configuredTier, "gun_spread_degrees", fallback, 0, 45);
            return GunSpreadPolicy.legacyAdjustedDegrees(legacyBase, type);
        }
        JsonObject mergedFirearmGroup = object(object(mergedTier, "weapon_spread_degrees"), "firearms");
        if (mergedFirearmGroup.has(type)) return number(mergedFirearmGroup, type, fallback, 0, 45);
        if (mergedTier.has("gun_spread_degrees")) {
            double legacyBase = number(mergedTier, "gun_spread_degrees", fallback, 0, 45);
            return GunSpreadPolicy.legacyAdjustedDegrees(legacyBase, type);
        }
        return fallback;
    }

    private static double projectileSpread(JsonObject configuredTier, JsonObject mergedTier,
            String type, double fallback) {
        JsonObject configuredProjectiles = object(
                object(configuredTier, "weapon_spread_degrees"), "projectiles");
        if (configuredProjectiles.has(type)) return number(configuredProjectiles, type, fallback, 0, 45);
        if (configuredTier.has("projectile_spread_degrees")) {
            double legacyBase = number(configuredTier, "projectile_spread_degrees", fallback, 0, 45);
            return GunSpreadPolicy.legacyProjectileDegrees(legacyBase, type);
        }
        JsonObject mergedProjectiles = object(object(mergedTier, "weapon_spread_degrees"), "projectiles");
        if (mergedProjectiles.has(type)) return number(mergedProjectiles, type, fallback, 0, 45);
        if (mergedTier.has("projectile_spread_degrees")) {
            double legacyBase = number(mergedTier, "projectile_spread_degrees", fallback, 0, 45);
            return GunSpreadPolicy.legacyProjectileDegrees(legacyBase, type);
        }
        return fallback;
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
