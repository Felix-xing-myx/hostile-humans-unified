package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The only faction/rank policy resolver. Builtins < datapack defaults < rank < explicit faction. */
public final class FactionEquipmentConfig {
    private static final JsonObject DEFAULTS = loadDefaults();
    private static final JsonObject DOCTRINE = loadResource("/defaults/equipment_doctrine.json");
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
            validateFactionId(id,"definitions");
            copy.put(id,data.deepCopy());
        });
        validateContribution(global,"equipment_loadouts");
        Prepared candidate=new Prepared(global,Map.copyOf(copy),0);
        Set<String> factions=new LinkedHashSet<>(BUILTIN_FACTIONS);
        factions.addAll(copy.keySet());
        collectOverrides(global,factions);
        for(String rank:RANKS) {
            collectOverrides(object(global,rank),factions);
            for(JsonObject definition:copy.values()) collectOverrides(object(definition,rank),factions);
        }
        for(String rank:RANKS) {
            requireObjectIfPresent(global,rank,"equipment_loadouts");
            JsonObject source=object(global,rank);
            validateContribution(source,"equipment_loadouts."+rank);
            for(var entry:copy.entrySet()) {
                requireObjectIfPresent(entry.getValue(),rank,"definitions."+entry.getKey());
                validateContribution(object(entry.getValue(),rank),"definitions."+entry.getKey()+"."+rank);
            }
            for(String faction:factions) resolve(source,rank,faction,candidate);
        }
        return candidate;
    }
    /** Coordinator publishes only after all definition modules have successfully prepared. */
    public static synchronized void publish(Prepared prepared) {
        Objects.requireNonNull(prepared);
        active=new Prepared(prepared.global,prepared.definitions,REVISIONS.incrementAndGet());
    }
    /** Startup convenience; runtime coordinated reloads should use prepare then publish. */
    public static void configure(JsonObject equipmentLoadouts,Map<String,JsonObject> factionDefaults) {
        publish(prepare(equipmentLoadouts,factionDefaults));
    }

    static Profile of(Human human) { return ConfiguredHumanEquipment.of(human).factions.profile(key(human)); }
    static String key(Human human) {
        if (TierAttributes.key(human).equals("roamer")) return "roamer";
        String faction = HumanFactions.enabled()?HumanFactions.shortId(human):"";
        return definedKey(faction,active);
    }
    static String definedKey(String faction,Prepared snapshot) {
        return faction!=null && (faction.equals("roamer")||faction.equals("neutral")||snapshot.definitions.containsKey(faction))?faction:"neutral";
    }

    static long revision() { return active.revision; }
    synchronized Profile profile(String faction) {
        Prepared snapshot=active;
        if(seenRevision!=snapshot.revision) { profiles.clear(); seenRevision=snapshot.revision; }
        // Builtin rows remain directly inspectable even before the coordinator publishes its first definitions.
        String defined=BUILTIN_FACTIONS.contains(faction)?faction:definedKey(faction,snapshot);
        return profiles.computeIfAbsent(defined,id->resolve(source,rank,id,snapshot));
    }

    private static Profile resolve(JsonObject source,String rank,String id,Prepared snapshot) {
        JsonObject ranks=object(DEFAULTS,rank);
        JsonObject data=object(ranks,ranks.has(id)?id:"neutral").deepCopy();
        JsonObject doctrineRank=object(DOCTRINE,rank);
        merge(data,object(doctrineRank,doctrineRank.has(id)?id:"neutral"));
        JsonObject definition=object(snapshot.definitions.getOrDefault(id,new JsonObject()),rank);
        JsonObject factionOverride=object(object(source,"faction_overrides"),id);
        merge(data,definition);
        for(String key:List.of("bands","item_pool","gun_category_map","equipment_archetypes")) {
            JsonObject combined=object(data,key).deepCopy();
            if(key.equals("equipment_archetypes")&&snapshot.global.has(key)&&object(snapshot.global,key).size()==0) combined=new JsonObject();
            else merge(combined,object(snapshot.global,key));
            if(key.equals("equipment_archetypes")&&source.has(key)&&object(source,key).size()==0) combined=new JsonObject();
            else merge(combined,object(source,key));
            data.add(key,combined);
        }
        for(var entry:source.entrySet()) if(!entry.getKey().equals("faction_overrides")
                && !List.of("bands","item_pool","gun_category_map","equipment_archetypes").contains(entry.getKey()))
            mergeEntry(data,entry.getKey(),entry.getValue());
        merge(data,factionOverride);
        // Armor is rank-only; faction definitions cannot sneak a second armor bias back in.
        data.add("armor_rank_band_weights",object(object(DOCTRINE,rank),"neutral").get("armor_rank_band_weights").deepCopy());
        JsonElement rankArmor=source.get("armor_band_weights");
        if(rankArmor!=null) data.add("armor_rank_band_weights",rankArmor.deepCopy());
        JsonObject armorBands=object(data,"bands").deepCopy();
        JsonElement armorThresholds=object(source,"bands").get("armor_points");
        if(armorThresholds==null) armorThresholds=object(snapshot.global,"bands").get("armor_points");
        if(armorThresholds==null) armorBands.remove("armor_points");
        else armorBands.add("armor_points",armorThresholds.deepCopy());
        data.add("bands",armorBands);
        boolean factionBands=(!id.equals("neutral")&&object(DEFAULTS,rank).has(id))
                || explicitBandPolicy(definition)||explicitBandPolicy(factionOverride);
        return new Profile(data,rank,id,factionBands);
    }

    static final class Profile {
        final JsonObject data;
        final String rank, faction;
        private final boolean factionBands;
        Profile(JsonObject data, String rank, String faction,boolean factionBands) {
            validateContribution(data,rank+"/"+faction);
            this.data = data; this.rank = rank; this.faction = faction; this.factionBands=factionBands;
        }
        boolean factionBands() { return factionBands; }
        boolean allowsMeleeFamily(String family) {
            if (data.has("melee_allowed_families")) {
                boolean allowed = false;
                for (var entry : data.getAsJsonArray("melee_allowed_families"))
                    if (entry.getAsString().equals(family)) allowed = true;
                if (!allowed) return false;
            }
            var weights = object(data, "melee_archetype_weights");
            // Empty maps preserve neutral/random loadouts; explicit zero families cannot leak via fallback.
            boolean configured = weights.keySet().stream().anyMatch(key -> !commentKey(key));
            return !configured || FactionEquipmentConfig.number(weights, family, 0, 0, 1e6) > 0;
        }
        SpawnMeleeReservePolicy meleeReserves() {
            if (!data.has("melee_reserves")) return null;
            JsonObject settings = object(data, "melee_reserves");
            if (!UnifiedConfig.bool(settings, "enabled", true)) return null;
            return new SpawnMeleeReservePolicy(
                    FactionEquipmentConfig.number(settings, "second_weapon_chance", 0, 0, 1),
                    FactionEquipmentConfig.number(settings, "third_weapon_chance", 0, 0, 1),
                    UnifiedConfig.bool(settings, "prefer_one_handed", true));
        }
        double number(String key, double fallback, double min, double max) { return FactionEquipmentConfig.number(data, key, fallback, min, max); }
        double[] weights(String key, int count) {
            if(key.equals("recovery_category_weights")&&data.has(key)&&data.getAsJsonArray(key).size()==3) {
                double[] old=array(data,key,3,null);
                return new double[]{0,old[0],old[1],old[2]};
            }
            return array(data, key, count, null);
        }
        double[] bands(String key, double... fallback) { return array(object(data, "bands"), key, fallback.length, fallback); }
        double[] shieldCounts() {
            JsonObject counts = object(data, "shield_count");
            return new double[]{FactionEquipmentConfig.number(counts,"0",0,0,1e6), FactionEquipmentConfig.number(counts,"1",1,0,1e6), FactionEquipmentConfig.number(counts,"2",0,0,1e6)};
        }
        boolean allowedItem(ResourceLocation id,boolean explicitExtra) {
            if(id==null || id.toString().equals("minecraft:air")) return false;
            JsonObject pool=object(data,"item_pool");
            boolean extra=explicitExtra;
            for(JsonElement entry:arrayOrEmpty(pool,"extra")) if(entry.getAsString().equals(id.toString())) extra=true;
            if(!extra&&pool.has("integrations")&&!pool.get("integrations").getAsBoolean()&&!id.getNamespace().equals("minecraft")) return false;
            for(JsonElement entry:arrayOrEmpty(pool,"deny")) {
                String denied=entry.getAsString();
                if(denied.equals(id.toString())||denied.equals(id.getNamespace())||denied.equals(id.getNamespace()+":*")) return false;
            }
            return true;
        }
        int pick(String key, int count, Human human) {
            int chosen = FactionEquipmentPolicy.choose(weights(key, count), human.getRandom().nextDouble());
            if (chosen < 0) warn(rank + "/" + faction + "/" + key, "All weights are zero; selection skipped");
            return chosen;
        }
    }

    private static final List<String> RANKS=List.of("roamer","tier1","tier2","tier3");
    private static final List<String> BUILTIN_FACTIONS=List.of("crimson","azure","verdant","ashen","roamer","neutral");
    private static final Set<String> GUN_CATEGORIES=Set.of("pistol","smg","shotgun","rifle","mg","sniper","launcher","other");

    private static void validateFactionId(String id,String path) {
        if(id==null||id.isEmpty()) invalid(path,"empty faction ID");
        if(id.contains(":")) {
            validateId(id,path,false,false);
            int colon=id.indexOf(':');
            if(colon<=0||colon==id.length()-1) invalid(path,"empty faction namespace or path");
        } else if(!id.matches("[a-z0-9/._-]+")) invalid(path,"invalid faction ID: "+id);
    }
    private static void collectOverrides(JsonObject source,Set<String> into) {
        for(var entry:object(source,"faction_overrides").entrySet()) if(!commentKey(entry.getKey())) {
            validateFactionId(entry.getKey(),"faction_overrides");
            into.add(entry.getKey());
            if(entry.getValue().isJsonObject()) collectOverrides(entry.getValue().getAsJsonObject(),into);
        }
    }
    private static boolean commentKey(String key) {
        return key.startsWith("_description")||key.startsWith("_说明")||key.equals("_comment")||key.equals("_comments");
    }
    private static boolean explicitBandPolicy(JsonObject source) {
        JsonObject bands=object(source,"bands");
        if(List.of("melee_damage","armor_points","toughness","shield_durability","ranged_damage","ranged_durability")
                .stream().anyMatch(bands::has)) return true;
        return List.of("melee_band_weights","armor_band_weights","toughness_band_weights","shield_band_weights","ranged_band_weights")
                .stream().anyMatch(source::has);
    }

    /** Pure schema validation. Unknown fields survive; explicitly invalid known fields reject the entire candidate. */
    private static void validateContribution(JsonObject root,String path) {
        for(String key:List.of("gun_chance","gun_core_chance","vanishing_chance","binding_chance","ranged_chance",
                "second_weapon_chance","third_weapon_chance","doctrine_core_chance")) strictNumber(root,key,path,0,1,false);
        for(String key:List.of("ammo_multiplier","food_multiplier","recovery_multiplier","self_buff_multiplier","throwable_multiplier"))
            strictNumber(root,key,path,0,100,false);
        for(String key:List.of("gun_fallback_uniform","allow_tacz_firearms","ranged_in_backpack","automatic_shield_enchantments")) strictBoolean(root,key,path);
        if(root.has("gun_fallback_mode")) {
            String mode=strictString(root.get("gun_fallback_mode"),path+".gun_fallback_mode");
            if(!Set.of("category_uniform","global_weight").contains(mode)) invalid(path+".gun_fallback_mode","unknown mode");
        }
        for(var entry:Map.of("melee_band_weights",5,"armor_band_weights",5,"toughness_band_weights",4,"shield_band_weights",4,
                "ranged_band_weights",4,"food_category_weights",4,"tactical_category_weights",7).entrySet())
            strictArray(root,entry.getKey(),entry.getValue(),path,false);
        if(root.has("recovery_category_weights")) {
            int size=strictJsonArray(root.get("recovery_category_weights"),path+".recovery_category_weights").size();
            if(size!=3&&size!=4) invalid(path+".recovery_category_weights","expected four categories (legacy three accepted)");
            strictArray(root,"recovery_category_weights",size,path,false);
        }
        strictArray(root,"armor_rank_band_weights",5,path,false);
        strictArray(root,"self_buff_category_weights",2,path,false);
        strictArray(root,"throwable_category_weights",5,path,false);
        requireObjectIfPresent(root, "melee_reserves", path);
        JsonObject meleeReserves = object(root, "melee_reserves");
        strictBoolean(meleeReserves, "enabled", path + ".melee_reserves");
        strictBoolean(meleeReserves, "prefer_one_handed", path + ".melee_reserves");
        for (String key : List.of("second_weapon_chance", "third_weapon_chance"))
            strictNumber(meleeReserves, key, path + ".melee_reserves", 0, 1, false);
        if (root.has("melee_allowed_families"))
            for (var entry : strictJsonArray(root.get("melee_allowed_families"), path + ".melee_allowed_families")) {
                String family = strictString(entry, path + ".melee_allowed_families");
                if (!Set.of("axe", "sword", "spear", "mace", "dagger", "other").contains(family))
                    invalid(path + ".melee_allowed_families", "unknown family " + family);
            }
        validateDoctrine(root,path);
        for(String key:List.of("bands","item_pool","gun_core","gun_category_map","enchant","durability","shield_count","faction_overrides"))
            requireObjectIfPresent(root,key,path);
        JsonObject bands=object(root,"bands");
        for(var entry:Map.of("melee_damage",4,"armor_points",4,"toughness",3,"shield_durability",3,"ranged_damage",3,"ranged_durability",3).entrySet())
            strictArray(bands,entry.getKey(),entry.getValue(),path+".bands",true);
        JsonObject pool=object(root,"item_pool");
        strictBoolean(pool,"integrations",path+".item_pool");
        validateIds(pool,"extra",path+".item_pool",false,false);
        validateIds(pool,"deny",path+".item_pool",false,true);
        JsonObject core=object(root,"gun_core");
        for(String key:core.keySet()) {
            if(commentKey(key)) continue;
            if(!GUN_CATEGORIES.contains(key)) invalid(path+".gun_core."+key,"unknown gun category");
            strictNumber(core,key,path+".gun_core",0,1e6,false);
        }
        JsonObject mapping=object(root,"gun_category_map");
        for(var entry:mapping.entrySet()) {
            if(commentKey(entry.getKey())) continue;
            validateId(entry.getKey(),path+".gun_category_map",true,false);
            if(!GUN_CATEGORIES.contains(strictString(entry.getValue(),path+".gun_category_map."+entry.getKey())))
                invalid(path+".gun_category_map."+entry.getKey(),"unknown gun category");
        }
        JsonObject enchant=object(root,"enchant");
        for(String key:List.of("weapon_chance","armor_chance")) strictNumber(enchant,key,path+".enchant",0,1,false);
        strictNumber(enchant,"budget",path+".enchant",0,10000,true);
        strictNumber(enchant,"max_enchants",path+".enchant",0,32,true);
        for(String key:List.of("include_modded","allow_treasure","allow_curses")) strictBoolean(enchant,key,path+".enchant");
        validateIds(enchant,"blacklist",path+".enchant",true,false);
        for(String key:List.of("bias","cost_overrides")) {
            requireObjectIfPresent(enchant,key,path+".enchant");
            JsonObject values=object(enchant,key);
            for(String id:values.keySet()) {
                if(commentKey(id)) continue;
                validateId(id,path+".enchant."+key,true,false);
                strictNumber(values,id,path+".enchant."+key,key.equals("cost_overrides")?1:0,key.equals("cost_overrides")?10000:1e6,key.equals("cost_overrides"));
            }
        }
        JsonObject durability=object(root,"durability");
        strictNumber(durability,"min",path+".durability",0,1,false);
        strictNumber(durability,"max",path+".durability",0,1,false);
        if(durability.has("min")&&durability.has("max")&&durability.get("min").getAsDouble()>durability.get("max").getAsDouble())
            invalid(path+".durability","min exceeds max");
        JsonObject shields=object(root,"shield_count");
        for(String key:shields.keySet()) {
            if(commentKey(key)) continue;
            if(!Set.of("0","1","2").contains(key)) invalid(path+".shield_count."+key,"expected 0, 1 or 2");
            strictNumber(shields,key,path+".shield_count",0,1e6,false);
        }
        JsonObject overrides=object(root,"faction_overrides");
        for(String id:overrides.keySet()) {
            if(commentKey(id)) continue;
            validateFactionId(id,path+".faction_overrides");
            requireObjectIfPresent(overrides,id,path+".faction_overrides");
            validateContribution(object(overrides,id),path+".faction_overrides."+id);
        }
        // Existing explicit candidate lists retain their semantics, but malformed IDs must fail before publication.
        for(String key:List.of("mainhand","inventory","ranged_mainhand","shield_pool")) validateItems(root,key,path);
        for(String key:List.of("offhand","bonus_mainhand")) {
            requireObjectIfPresent(root,key,path);
            JsonObject nested=object(root,key);
            strictNumber(nested,"chance",path+"."+key,0,1,false);
            validateItems(nested,"entries",path+"."+key);
        }
        if(root.has("armor_sets")) {
            JsonArray entries=strictJsonArray(root.get("armor_sets"),path+".armor_sets");
            for(JsonElement entry:entries) {
                if(!entry.isJsonObject()) invalid(path+".armor_sets","entry must be an object");
                JsonObject set=entry.getAsJsonObject();
                for(String slot:List.of("head","chest","legs","feet")) if(set.has(slot)&&!set.get(slot).isJsonNull())
                    validateId(strictString(set.get(slot),path+".armor_sets."+slot),path+".armor_sets."+slot,false,false);
                strictNumber(set,"weight",path+".armor_sets",0,1e6,true);
            }
        }
    }
    public static double rangedChance(Human human) { return of(human).number("ranged_chance",.2,0,1); }

    private static void validateDoctrine(JsonObject root,String path) {
        Map<String,Set<String>> families=Map.of("melee",Set.of("axe","sword","spear","mace","dagger","other"),
                "shield",Set.of("buckler","small","round","tower","other"),"ranged",Set.of("bow","crossbow","trident","other"));
        for(var category:families.entrySet()) {
            String key=category.getKey()+"_archetype_weights";
            requireObjectIfPresent(root,key,path);
            for(String family:object(root,key).keySet()) {
                if(commentKey(family)) continue;
                if(!category.getValue().contains(family)) invalid(path+"."+key,"unknown family "+family);
                strictNumber(object(root,key),family,path+"."+key,0,1e6,false);
            }
        }
        requireObjectIfPresent(root,"equipment_archetypes",path);
        for(var entry:object(root,"equipment_archetypes").entrySet()) {
            if(commentKey(entry.getKey())) continue;
            if(families.containsKey(entry.getKey())) {
                if(!entry.getValue().isJsonObject()) invalid(path,"family mapping category must be an object");
                for(var family:entry.getValue().getAsJsonObject().entrySet()) {
                    if(commentKey(family.getKey())) continue;
                    if(!families.get(entry.getKey()).contains(family.getKey())) invalid(path,"unknown equipment family");
                    for(var selector:strictJsonArray(family.getValue(),path+".equipment_archetypes"))
                        validateId(strictString(selector,path),path+".equipment_archetypes",true,false);
                }
            } else {
                validateId(entry.getKey(),path+".equipment_archetypes",true,false);
                String family=strictString(entry.getValue(),path+".equipment_archetypes");
                if(families.values().stream().noneMatch(values->values.contains(family))) invalid(path,"unknown equipment family "+family);
            }
        }
        requireObjectIfPresent(root,"faction_armor",path);
        // Malformed individual sets are isolated at spawn, rather than rejecting all remaining valid sets.
        JsonObject armor=object(root,"faction_armor");
        if(armor.has("sets")) strictJsonArray(armor.get("sets"),path+".faction_armor.sets");
    }

    private static JsonArray arrayOrEmpty(JsonObject root,String key) {
        return root.has(key)?root.getAsJsonArray(key):new JsonArray();
    }
    private static void validateItems(JsonObject root,String key,String path) {
        if(!root.has(key)) return;
        for(JsonElement entry:strictJsonArray(root.get(key),path+"."+key)) {
            if(!entry.isJsonObject()) invalid(path+"."+key,"entry must be an object");
            JsonObject item=entry.getAsJsonObject();
            if(!item.has("item")) invalid(path+"."+key,"missing item ID");
            validateId(strictString(item.get("item"),path+"."+key+".item"),path+"."+key+".item",false,false);
            strictNumber(item,"weight",path+"."+key,0,1e6,true);
            strictNumber(item,"count",path+"."+key,1,64,true);
        }
    }
    private static void validateIds(JsonObject root,String key,String path,boolean tags,boolean namespaces) {
        if(!root.has(key)) return;
        for(JsonElement id:strictJsonArray(root.get(key),path+"."+key)) validateId(strictString(id,path+"."+key),path+"."+key,tags,namespaces);
    }
    private static void validateId(String text,String path,boolean tags,boolean namespaces) {
        String value=text;
        if(tags&&value.startsWith("#")) value=value.substring(1);
        else if(namespaces&&!value.contains(":")) {
            if(value.matches("[a-z0-9_.-]+")) return;
            invalid(path,"invalid namespace: "+text);
        } else if(namespaces&&value.endsWith(":*")) {
            if(value.substring(0,value.length()-2).matches("[a-z0-9_.-]+")) return;
            invalid(path,"invalid namespace wildcard: "+text);
        }
        if(!value.contains(":")||ResourceLocation.tryParse(value)==null) invalid(path,"invalid ID: "+text);
    }
    private static void strictNumber(JsonObject root,String key,String path,double min,double max,boolean integer) {
        if(!root.has(key)) return;
        JsonElement value=root.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()) invalid(path+"."+key,"expected numeric value");
        double number=value.getAsDouble();
        if(!Double.isFinite(number)||number<min||number>max||(integer&&number!=Math.rint(number)))
            invalid(path+"."+key,"out of range or non-finite numeric value");
    }
    private static void strictBoolean(JsonObject root,String key,String path) {
        if(root.has(key)&&(!root.get(key).isJsonPrimitive()||!root.get(key).getAsJsonPrimitive().isBoolean())) invalid(path+"."+key,"expected boolean");
    }
    private static void strictArray(JsonObject root,String key,int length,String path,boolean increasing) {
        if(!root.has(key)) return;
        JsonArray values=strictJsonArray(root.get(key),path+"."+key);
        if(values.size()!=length) invalid(path+"."+key,"wrong array dimension");
        double previous=-1;
        for(int index=0;index<values.size();index++) {
            JsonObject number=new JsonObject(); number.add("value",values.get(index));
            strictNumber(number,"value",path+"."+key+"["+index+"]",0,1e6,false);
            double current=values.get(index).getAsDouble();
            if(increasing&&index>0&&current<=previous) invalid(path+"."+key,"thresholds must be strictly increasing");
            previous=current;
        }
    }
    private static String strictString(JsonElement value,String path) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()) invalid(path,"expected string");
        return value.getAsString();
    }
    private static JsonArray strictJsonArray(JsonElement value,String path) {
        if(!value.isJsonArray()) invalid(path,"expected array");
        return value.getAsJsonArray();
    }
    private static void requireObjectIfPresent(JsonObject root,String key,String path) {
        if(root.has(key)&&!root.get(key).isJsonObject()) invalid(path+"."+key,"expected object");
    }
    private static void invalid(String path,String detail) { throw new IllegalArgumentException("Invalid faction equipment config "+path+": "+detail); }

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
        if(value.isJsonObject()&&value.getAsJsonObject().size()==0
                &&(key.endsWith("_archetype_weights")||key.equals("equipment_archetypes")||key.equals("faction_armor"))) {
            into.add(key,new JsonObject());
            return;
        }
        if (value.isJsonObject() && into.has(key) && into.get(key).isJsonObject()) merge(into.getAsJsonObject(key),value.getAsJsonObject());
        else into.add(key,value.deepCopy());
    }
    static void warn(String key, String message) { if (WARNED.add(key)) HumanGunner.LOGGER.warn("{}: {}",message,key); }
    private static JsonObject loadDefaults() {
        return loadResource("/defaults/faction_equipment.json");
    }
    private static JsonObject loadResource(String path) {
        try (var stream=FactionEquipmentConfig.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing faction equipment defaults");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load faction equipment defaults",error); }
    }
}
