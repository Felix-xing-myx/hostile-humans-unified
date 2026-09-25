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

    public record Tier(int healthMin, int healthMax, double normalSpeed, double combatSpeed,
            double retreatSpeed, double attackDamage, double armor, double armorToughness,
            double knockbackResistance, double followRange, double meleeDamage, double bowDamage,
            double tridentDamage, double incomingDamage, double spawnMultiplier,
            double gunSpreadDegrees, double projectileSpreadDegrees,
            int meleeCooldownMin, int meleeCooldownMax) {}
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
            JsonObject d = defaults.getAsJsonObject("tiers").getAsJsonObject(key);
            int low = (int) number(t, "health_min", d.get("health_min").getAsDouble(), 1, 1024);
            int high = (int) number(t, "health_max", d.get("health_max").getAsDouble(), 1, 1024);
            int meleeCooldownLow = (int) number(t, "melee_cooldown_min",
                    d.get("melee_cooldown_min").getAsDouble(), 1, 200);
            int meleeCooldownHigh = (int) number(t, "melee_cooldown_max",
                    d.get("melee_cooldown_max").getAsDouble(), 1, 200);
            values.put(key, new Tier(Math.min(low, high), Math.max(low, high),
                    number(t, "normal_movement_speed", .1, .01, 2),
                    number(t, "combat_movement_speed", .1, .01, 2),
                    number(t, "retreat_movement_speed", .1, .01, 2),
                    number(t, "attack_damage", 1, 0, 2048), number(t, "armor", 0, 0, 30),
                    number(t, "armor_toughness", 0, 0, 20),
                    number(t, "knockback_resistance", key.equals("tier3") ? .15 : 0, 0, 1),
                    number(t, "follow_range", key.equals("tier3") ? 48 : 40, 8, 128),
                    number(t, "melee_damage_multiplier", 1, 0, 10),
                    number(t, "bow_damage_multiplier", 1, 0, 10),
                    number(t, "trident_damage_multiplier", 1, 0, 10),
                    number(t, "incoming_damage_multiplier", 1, 0, 10),
                    number(t, "spawn_multiplier", d.get("spawn_multiplier").getAsDouble(), 0, 100),
                    number(t, "gun_spread_degrees", d.get("gun_spread_degrees").getAsDouble(), 0, 45),
                    number(t, "projectile_spread_degrees", d.get("projectile_spread_degrees").getAsDouble(), 0, 45),
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
            if (Set.of("normal_movement_speed", "combat_movement_speed", "retreat_movement_speed").contains(entry.getKey())) {
                for (JsonElement tier : data.getAsJsonObject("tiers").asMap().values())
                    tier.getAsJsonObject().add(entry.getKey(), entry.getValue().deepCopy());
            } else data.getAsJsonObject("ai").add(entry.getKey(), entry.getValue().deepCopy());
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
