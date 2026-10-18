package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The only faction/rank policy resolver. Builtins < datapack defaults < rank < explicit faction. */
public final class FactionEquipmentConfig {
    private static final JsonObject DEFAULTS = loadDefaults();
    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicLong REVISIONS=new java.util.concurrent.atomic.AtomicLong();
    private static volatile Prepared active=new Prepared(new JsonObject(),Map.of(),0);
    public static final class Prepared {
        private final JsonObject global;
        private final Map<String,JsonObject> definitions;
        private final long revision;
        private Prepared(JsonObject global,Map<String,JsonObject> definitions,long revision) {
            this.global=global; this.definitions=definitions; this.revision=revision;
        }
    }
    private long seenRevision=-1;
    final String rank;
    final JsonObject source;
    private final Map<String, Profile> profiles = new HashMap<>();

    FactionEquipmentConfig(JsonObject source, String rank) {
        this.source = source.deepCopy(); this.rank = rank;
    }

    /** Pure preparation: no active state changes, entity mutation, optional API calls or item-registry traversal. */
    public static Prepared prepare(JsonObject equipmentLoadouts, Map<String, JsonObject> factionDefaults) {
        JsonObject global=equipmentLoadouts==null?new JsonObject():equipmentLoadouts.deepCopy();
        Map<String,JsonObject> copy=new LinkedHashMap<>();
        if(factionDefaults!=null) factionDefaults.forEach((id,data)-> {
            if(id==null||data==null) throw new IllegalArgumentException("Null faction equipment definition");
            copy.put(id,data.deepCopy());
        });
        return new Prepared(global,Map.copyOf(copy),REVISIONS.incrementAndGet());
    }
    /** Coordinator publishes only after all definition modules have successfully prepared. */
    public static void publish(Prepared prepared) { active=Objects.requireNonNull(prepared); }
    /** Startup convenience; runtime coordinated reloads should use prepare then publish. */
    public static void configure(JsonObject equipmentLoadouts,Map<String,JsonObject> factionDefaults) {
        publish(prepare(equipmentLoadouts,factionDefaults));
    }

    static Profile of(Human human) { return ConfiguredHumanEquipment.of(human).factions.profile(key(human)); }
    static String key(Human human) {
        if (TierAttributes.key(human).equals("roamer")) return "roamer";
        String faction = HumanFactions.enabled()?HumanFactions.shortId(human):"";
        return List.of("crimson", "azure", "verdant", "ashen").contains(faction) ? faction : "neutral";
    }

    static long revision() { return active.revision; }
    synchronized Profile profile(String faction) {
        Prepared snapshot=active;
        if(seenRevision!=snapshot.revision) { profiles.clear(); seenRevision=snapshot.revision; }
        return profiles.computeIfAbsent(faction, id -> {
            JsonObject ranks = object(DEFAULTS, rank);
            JsonObject data = object(ranks, ranks.has(id) ? id : "neutral").deepCopy();
            merge(data, object(snapshot.definitions.getOrDefault(id, new JsonObject()), rank));
            // Global defaults are a single authority; rank values override them.
            for (String key : List.of("bands", "item_pool", "gun_category_map")) {
                JsonObject combined = object(data,key).deepCopy();
                merge(combined,object(snapshot.global,key));
                merge(combined, object(source, key));
                data.add(key, combined);
            }
            for (var entry : source.entrySet()) if (!entry.getKey().equals("faction_overrides")
                    && !List.of("bands", "item_pool", "gun_category_map").contains(entry.getKey()))
                mergeEntry(data, entry.getKey(), entry.getValue());
            merge(data, object(object(source, "faction_overrides"), id));
            return new Profile(data, rank, id);
        });
    }

    static final class Profile {
        final JsonObject data;
        final String rank, faction;
        Profile(JsonObject data, String rank, String faction) { this.data = data; this.rank = rank; this.faction = faction; }
        boolean factionBands() { return !faction.equals("neutral"); }
        double number(String key, double fallback, double min, double max) { return FactionEquipmentConfig.number(data, key, fallback, min, max); }
        double[] weights(String key, int count) { return array(data, key, count, null); }
        double[] bands(String key, double... fallback) { return array(object(data, "bands"), key, fallback.length, fallback); }
        double[] shieldCounts() {
            JsonObject counts = object(data, "shield_count");
            return new double[]{FactionEquipmentConfig.number(counts,"0",0,0,1e6), FactionEquipmentConfig.number(counts,"1",1,0,1e6), FactionEquipmentConfig.number(counts,"2",0,0,1e6)};
        }
        int pick(String key, int count, Human human) {
            int chosen = FactionEquipmentPolicy.choose(weights(key, count), human.getRandom().nextDouble());
            if (chosen < 0) warn(rank + "/" + faction + "/" + key, "All weights are zero; selection skipped");
            return chosen;
        }
    }

    static double number(JsonObject root, String key, double fallback, double min, double max) {
        try {
            JsonElement value = root.get(key);
            if (value == null) return fallback;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            double number = value.getAsDouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException();
            return Math.max(min, Math.min(max, number));
        } catch (RuntimeException invalid) { warn(key, "Invalid numeric equipment value; using default"); return fallback; }
    }

    static double[] array(JsonObject root, String key, int count, double[] fallback) {
        double[] result = fallback == null ? new double[count] : fallback.clone();
        JsonElement element = root.get(key);
        if (element == null) return result;
        try {
            JsonArray values = element.getAsJsonArray();
            if (values.size() != count) throw new IllegalArgumentException();
            for (int i=0; i<count; i++) {
                if (!values.get(i).isJsonPrimitive() || !values.get(i).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                result[i] = values.get(i).getAsDouble();
                if (!Double.isFinite(result[i]) || result[i]<0 || result[i]>1e6
                        || (fallback != null && i>0 && result[i]<=result[i-1])) throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException invalid) { warn(key, "Invalid equipment array; using default"); return fallback == null ? new double[count] : fallback.clone(); }
    }
    static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }
    static void merge(JsonObject into, JsonObject from) { from.entrySet().forEach(e -> mergeEntry(into,e.getKey(),e.getValue())); }
    private static void mergeEntry(JsonObject into, String key, JsonElement value) {
        if (value.isJsonObject() && into.has(key) && into.get(key).isJsonObject()) merge(into.getAsJsonObject(key),value.getAsJsonObject());
        else into.add(key,value.deepCopy());
    }
    static void warn(String key, String message) { if (WARNED.add(key)) HumanGunner.LOGGER.warn("{}: {}",message,key); }
    private static JsonObject loadDefaults() {
        try (var stream=FactionEquipmentConfig.class.getResourceAsStream("/defaults/faction_equipment.json")) {
            if (stream == null) throw new IllegalStateException("Missing faction equipment defaults");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load faction equipment defaults",error); }
    }
}
