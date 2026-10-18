package club.someoneice.humangunner;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One immutable definition revision; malformed reloads never publish a partial relationship table. */
final class FactionDefinitions extends SimplePreparableReloadListener<FactionDefinitions.Prepared> {
    static final List<String> BUILTINS = List.of("crimson", "azure", "verdant", "ashen");
    static final List<String> DIMENSIONS = List.of("health", "armor", "toughness", "knockback_resistance",
            "speed", "detection", "melee", "gun", "projectile", "accuracy", "regeneration");
    static final FactionDefinitions INSTANCE = new FactionDefinitions();
    record Definition(ResourceLocation id, int color, String name, int startingStanding, int weight,
                      Set<ResourceLocation> enemies, Set<ResourceLocation> allies, List<Double> preference,
                      List<Double> bossPreference, String bandStyle, String aggression,
                      ResourceLocation hireItem, List<Integer> hireCosts, List<Integer> hireStanding, double friendlyDiscount, double allyDiscount, JsonObject equipment) {}
    record Revision(long number, Map<ResourceLocation, Definition> definitions, SpecialtyData.Definitions specialty) {}
    record Prepared(Map<ResourceLocation, JsonElement> factions, Map<ResourceLocation, JsonElement> specialty) {}
    private static volatile Revision current = new Revision(0, builtins(), SpecialtyData.get());

    private FactionDefinitions() {}
    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Prepared(read(manager, "factions"), read(manager, "specialty"));
    }
    private static Map<ResourceLocation, JsonElement> read(ResourceManager manager, String directory) {
        Map<ResourceLocation, JsonElement> values = new HashMap<>();
        manager.listResources(directory, location -> location.getPath().endsWith(".json")).forEach((path, resource) -> {
            String local = path.getPath().substring(directory.length() + 1, path.getPath().length() - 5);
            ResourceLocation key = new ResourceLocation(path.getNamespace(), local);
            try (var reader = resource.openAsReader()) { values.put(key, JsonParser.parseReader(reader)); }
            catch (Exception invalid) {
                // Carry a parse failure through apply so it rejects the complete candidate revision.
                values.put(key, JsonNull.INSTANCE);
            }
        });
        return Map.copyOf(values);
    }
    static Revision current() { return current; }
    static void publishSpecialty(SpecialtyData.Definitions specialty) {
        Revision previous = current;
        if (previous != null) current = new Revision(previous.number() + 1, previous.definitions(), specialty);
    }
    static ResourceLocation id(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return ResourceLocation.tryParse(raw.contains(":") ? raw : HumanGunner.MOD_ID + ":" + raw);
    }
    static Definition find(String raw) { ResourceLocation key = id(raw); return key == null ? null : current.definitions().get(key); }
    static boolean enemies(String first, String second) {
        Definition a = find(first), b = find(second);
        return a != null && b != null && !a.id().equals(b.id())
                && (a.enemies().contains(b.id()) || b.enemies().contains(a.id()));
    }
    static boolean allies(String first, String second) {
        Definition a = find(first), b = find(second);
        return a != null && b != null && (a.id().equals(b.id())
                || (!enemies(first, second) && (a.allies().contains(b.id()) || b.allies().contains(a.id()))));
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager,
                                   ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, Definition> next = new TreeMap<>();
            for (var entry : prepared.factions().entrySet()) next.put(entry.getKey(), parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            if (next.isEmpty()) throw new IllegalArgumentException("No faction definitions");
            if (next.size() > 256) throw new IllegalArgumentException("Too many faction definitions");
            for (Definition d : next.values()) {
                for (ResourceLocation related : d.enemies()) if (!next.containsKey(related))
                    throw new IllegalArgumentException("Unknown enemy " + related + " in " + d.id());
                for (ResourceLocation related : d.allies()) if (!next.containsKey(related) || d.enemies().contains(related))
                    throw new IllegalArgumentException("Invalid ally " + related + " in " + d.id());
            }
            SpecialtyData.Definitions specialty = SpecialtyData.parse(prepared.specialty());
            var equipment = FactionEquipmentConfig.prepare(UnifiedConfig.get().equipmentSource(), equipmentDefaults(next));
            // All modules validate first; publication occurs synchronously on the server reload thread.
            FactionEquipmentConfig.publish(equipment);
            // Readers of both identities and allocation data observe this single publication.
            current = new Revision(current.number() + 1, Map.copyOf(next), specialty);
        } catch (RuntimeException invalid) {
            LogUtils.getLogger().error("Keeping previous faction revision: {}", invalid.getMessage());
        }
    }
    static Map<String, JsonObject> equipmentDefaults() { return equipmentDefaults(current.definitions()); }
    private static Map<String, JsonObject> equipmentDefaults(Map<ResourceLocation, Definition> definitions) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (Definition d : definitions.values()) {
            String key = d.id().getNamespace().equals(HumanGunner.MOD_ID) ? d.id().getPath() : d.id().toString();
            result.put(key, d.equipment().deepCopy());
        }
        return Map.copyOf(result);
    }
    static void reset() { current = new Revision(current.number() + 1, builtins(), SpecialtyData.parse(Map.of())); }
    private static Map<ResourceLocation, Definition> builtins() {
        Map<ResourceLocation, Definition> result = new LinkedHashMap<>();
        for (String name : BUILTINS) {
            try (var stream = Objects.requireNonNull(FactionDefinitions.class.getResourceAsStream(
                    "/data/hostile_humans_unified/factions/" + name + ".json"));
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                ResourceLocation key = id(name);
                result.put(key, parse(key, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception invalid) { throw new IllegalStateException("Invalid built-in faction " + name, invalid); }
        }
        return Map.copyOf(result);
    }
    private static Definition parse(ResourceLocation key, JsonObject root) {
        if (key.toString().length() > 256 || UnifiedConfig.string(root, "name", "").length() > 128)
            throw new IllegalArgumentException("Faction display identity exceeds protocol limits");
        String color = root.get("color").getAsString();
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid color " + key);
        JsonObject hire = UnifiedConfig.object(root, "hire");
        ResourceLocation item = id(UnifiedConfig.string(hire, "item", "minecraft:iron_ingot"));
        if (item == null) throw new IllegalArgumentException("Invalid hiring item " + key);
        List<Integer> costs = new ArrayList<>();
        JsonArray configured = hire.has("costs") ? hire.getAsJsonArray("costs") : JsonParser.parseString("[8,16,32]").getAsJsonArray();
        if (configured.size() != 3) throw new IllegalArgumentException("Three hiring costs required " + key);
        for (JsonElement value : configured) {
            int cost = value.getAsBigDecimal().intValueExact();
            if (cost < 0 || cost > 4096) throw new IllegalArgumentException("Invalid hiring cost " + key);
            costs.add(cost);
        }
        String style = UnifiedConfig.string(root, "band_style", "base"), aggression = UnifiedConfig.string(root, "aggression", "aggressive");
        if (!Set.of("base", "none").contains(style) || !Set.of("aggressive", "defensive", "passive").contains(aggression))
            throw new IllegalArgumentException("Unknown faction style/aggression " + key);
        List<Integer> hireStanding = new ArrayList<>();
        JsonElement minimum = root.get("hire_standing");
        for (int tier = 1; tier <= 3; tier++) {
            JsonElement value = minimum != null && minimum.isJsonObject() ? minimum.getAsJsonObject().get("tier" + tier) : minimum;
            hireStanding.add(value == null ? (tier == 3 ? 150 : 50) : standing(value.getAsString()));
        }
        int starting = root.has("starting_standing") ? integer(root, "starting_standing", -200, -500, 500)
                : initialStanding(UnifiedConfig.string(root, "player_relation", "hostile"));
        JsonObject discounts = UnifiedConfig.object(hire, "discount");
        List<Double> preference = vector(root.get("allocation_preference"), Collections.nCopies(11, 1.0));
        return new Definition(key, Integer.parseInt(color.substring(1), 16), UnifiedConfig.string(root, "name", ""),
                starting, integer(root, "spawn_weight", 0, 0, 1000000),
                ids(root, "enemies"), ids(root, "allies"), preference, vector(root.get("boss_preference"), preference),
                style, aggression, item, List.copyOf(costs), List.copyOf(hireStanding),
                UnifiedConfig.number(discounts, "friendly", UnifiedConfig.number(hire, "friendly_discount", .9, 0, 1), 0, 1),
                UnifiedConfig.number(discounts, "allied", UnifiedConfig.number(hire, "ally_discount", .8, 0, 1), 0, 1), equipment(root));
    }
    private static JsonObject equipment(JsonObject root) {
        if (!root.has("equipment")) return new JsonObject();
        if (!root.get("equipment").isJsonObject()) throw new IllegalArgumentException("equipment must contain rank objects");
        return root.getAsJsonObject("equipment").deepCopy();
    }
    private static int standing(String name) {
        return switch (name) {
            case "nemesis" -> -500; case "hostile" -> -299; case "wary" -> -99;
            case "neutral" -> 50; case "favorable" -> 150; case "friendly" -> 250; case "allied" -> 400;
            default -> throw new IllegalArgumentException("Unknown standing tier " + name);
        };
    }
    private static int initialStanding(String name) {
        return switch (name) {
            case "nemesis" -> -400; case "hostile" -> -200; case "wary" -> 0; case "neutral" -> 100;
            case "favorable" -> 200; case "friendly" -> 300; case "allied" -> 450;
            default -> throw new IllegalArgumentException("Unknown initial relationship " + name);
        };
    }
    private static int integer(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key)) return fallback;
        int value = object.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
    private static Set<ResourceLocation> ids(JsonObject root, String field) {
        Set<ResourceLocation> result = new HashSet<>();
        if (root.has(field)) for (JsonElement value : root.getAsJsonArray(field)) {
            ResourceLocation id = id(value.getAsString());
            if (id == null) throw new IllegalArgumentException("Invalid " + field);
            result.add(id);
        }
        return Set.copyOf(result);
    }
    private static List<Double> vector(JsonElement raw, List<Double> fallback) {
        if (raw == null) return fallback;
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            double value = raw.isJsonArray() ? raw.getAsJsonArray().get(i).getAsDouble()
                    : raw.getAsJsonObject().has(DIMENSIONS.get(i)) ? raw.getAsJsonObject().get(DIMENSIONS.get(i)).getAsDouble() : 1;
            if (!Double.isFinite(value) || value <= 0 || value > 100) throw new IllegalArgumentException("Invalid allocation preference");
            values.add(value);
        }
        if (raw.isJsonArray() && raw.getAsJsonArray().size() != 11) throw new IllegalArgumentException("Eleven dimensions required");
        return List.copyOf(values);
    }
}
