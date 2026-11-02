package dev.felix.hostilehumans.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Raw-file migration must not erase faction defaults or overwrite a new explicit policy. */
public final class EquipmentConfigMigrationTest {
    public static void main(String[] args) {
        JsonObject custom = json("{\"tacz\":{\"gun_chance\":0.23},\"author_note\":\"keep\"}");
        check(EquipmentConfigMigration.migrate(custom,"tier1"), "first migration");
        check(custom.getAsJsonObject("equipment_loadout").get("gun_chance").getAsDouble()==.23,"custom probability retained");
        check(custom.get("author_note").getAsString().equals("keep"),"unknown fields retained");
        custom.getAsJsonObject("tacz").addProperty("gun_chance",.8);
        check(!EquipmentConfigMigration.migrate(custom,"tier1")&&custom.getAsJsonObject("equipment_loadout").get("gun_chance").getAsDouble()==.23,"receipt prevents second import");
        JsonObject explicit=json("{\"tacz\":{\"gun_chance\":0.23},\"equipment_loadout\":{\"gun_chance\":0.0}}");
        EquipmentConfigMigration.migrate(explicit,"tier2");
        check(explicit.getAsJsonObject("equipment_loadout").get("gun_chance").getAsDouble()==0,"explicit zero new policy wins");
        for(String rank:java.util.List.of("roamer","tier1","tier2","tier3")) {
            double old=switch(rank){case "roamer"->.08;case "tier1"->.1;case "tier2"->.4;default->.7;};
            JsonObject shipped=json("{\"tacz\":{\"gun_chance\":"+old+"}}");
            EquipmentConfigMigration.migrate(shipped,rank);
            check(!shipped.getAsJsonObject("equipment_loadout").has("gun_chance"),"old shipped default does not flatten faction policies: "+rank);
        }
        checkTotemMigration();
        System.out.println("Equipment config migration checks passed");
    }
    private static void checkTotemMigration() {
        JsonObject rank=json("""
                {"equipment_loadout":{"totem_chance":0.0,"offhand":{"chance":0.5,"entries":[
                  {"item":"minecraft:totem_of_undying","weight":15},
                  {"item":"minecraft:shield","weight":85},
                  {"item":"minecraft:golden_apple","weight":2}]}},
                 "growth_profile":{"early_totem_keep_chance":0.42,"start_day":17},"author_note":"keep"}
                """);
        check(SpawnSupplyMigration.migrateRank(rank),"retired totem controls migrate even without potion pools");
        var loadout=rank.getAsJsonObject("equipment_loadout");
        check(loadout.get("totem_chance").getAsDouble()==0,"explicit disabled direct chance preserved");
        check(loadout.getAsJsonObject("offhand").getAsJsonArray("entries").size()==2,"other custom offhand entries preserved");
        check(loadout.getAsJsonObject("offhand").get("chance").getAsDouble()==.5,"other supply chance preserved");
        check(!rank.getAsJsonObject("growth_profile").has("early_totem_keep_chance"),"retired growth control removed");
        check(rank.getAsJsonObject("growth_profile").get("start_day").getAsInt()==17,"growth curve preserved");
        check(rank.get("author_note").getAsString().equals("keep"),"author note preserved");
        check(!SpawnSupplyMigration.migrateRank(rank),"totem migration is idempotent");
        check(SpawnTotemSupply.defaultChance("roamer")==.05 && SpawnTotemSupply.defaultChance("tier1")==.05
                && SpawnTotemSupply.defaultChance("tier2")==.10 && SpawnTotemSupply.defaultChance("tier3")==.20,
                "all rank direct defaults agree with the design");
    }
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
