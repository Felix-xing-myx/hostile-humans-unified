package club.someoneice.humangunner;

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
        System.out.println("Equipment config migration checks passed");
    }
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
