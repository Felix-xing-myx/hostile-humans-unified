package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.config.TierConfigProjection;
import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Raw-file migration, before any default fill can hide explicit legacy empty/custom pools. */
public final class SpawnSupplyMigration {
    private SpawnSupplyMigration() {}

    public static void migrateRoot(JsonObject root) {
        migrateRank(root);
        JsonObject tiers=UnifiedConfig.object(root,"tiers");
        for(String rank:TierConfigProjection.RANKS) if(tiers.has(rank)&&tiers.get(rank).isJsonObject())
            migrateRank(tiers.getAsJsonObject(rank));
        JsonObject loadouts=UnifiedConfig.object(root,"equipment_loadouts");
        for(String rank:TierConfigProjection.RANKS) if(loadouts.has(rank)&&loadouts.get(rank).isJsonObject())
            retireTotemPool(loadouts.getAsJsonObject(rank));
    }

    public static boolean migrateRank(JsonObject rank) {
        boolean changed=retireTotemPool(UnifiedConfig.object(rank,"equipment_loadout"));
        JsonObject growth=UnifiedConfig.object(rank,"growth_profile");
        for(String key:List.of("early_totem_keep_chance", "_说明_early_totem_keep_chance_中文",
                "_description_early_totem_keep_chance_en"))
            changed |= growth.remove(key) != null;
        JsonObject potions=UnifiedConfig.object(rank,"potions");
        JsonObject legacy=UnifiedConfig.object(potions,"tactical");
        if(!legacy.has("pool")||!legacy.get("pool").isJsonArray()) return changed;
        for(String key:List.of("self_buffs","throwables")) {
            if(potions.has(key)&&!potions.get(key).isJsonObject()) continue;
            JsonObject target=UnifiedConfig.object(potions,key);
            if(target.has("pool")) continue; // Explicit new empty pools and weights always win.
            JsonArray selected=new JsonArray();
            for(var element:legacy.getAsJsonArray("pool")) {
                if(!element.isJsonObject()) continue;
                var entry=SpawnPotionConfig.parseEntry(element.getAsJsonObject(),true);
                if(entry!=null&&SpawnPotionConfig.category(entry,key.equals("self_buffs")?"self_buff":"throwable")>=0)
                    selected.add(element.deepCopy());
            }
            target.add("pool",selected);
            // Shared legacy counts cannot be reused twice. Missing independent totals use new rank defaults.
            potions.add(key,target);
            changed=true;
        }
        return changed;
    }

    /** Retire only totem entries; preserve other explicit candidates and the new direct chance. */
    private static boolean retireTotemPool(JsonObject loadout) {
        JsonObject off=UnifiedConfig.object(loadout,"offhand");
        if(!off.has("entries") || !off.get("entries").isJsonArray()) return false;
        JsonArray entries=off.getAsJsonArray("entries");
        boolean changed=false;
        for(int i=entries.size()-1;i>=0;i--) {
            var value=entries.get(i);
            if(!value.isJsonObject()) continue;
            var id=value.getAsJsonObject().get("item");
            if(id!=null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()
                    && (id.getAsString().equals("minecraft:totem_of_undying") || id.getAsString().equals("totem_of_undying"))) {
                entries.remove(i); changed=true;
            }
        }
        return changed;
    }
}
