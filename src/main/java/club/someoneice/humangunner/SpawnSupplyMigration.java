package club.someoneice.humangunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Raw-file migration, before any default fill can hide explicit legacy empty/custom pools. */
final class SpawnSupplyMigration {
    private SpawnSupplyMigration() {}

    static void migrateRoot(JsonObject root) {
        migrateRank(root);
        JsonObject tiers=UnifiedConfig.object(root,"tiers");
        for(String rank:TierConfigProjection.RANKS) if(tiers.has(rank)&&tiers.get(rank).isJsonObject())
            migrateRank(tiers.getAsJsonObject(rank));
    }

    static boolean migrateRank(JsonObject rank) {
        JsonObject potions=UnifiedConfig.object(rank,"potions");
        JsonObject legacy=UnifiedConfig.object(potions,"tactical");
        if(!legacy.has("pool")||!legacy.get("pool").isJsonArray()) return false;
        boolean changed=false;
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
}
