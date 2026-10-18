package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/** Rank/faction precedence, independent profile identity and all shipped weight rows. */
public final class FactionEquipmentConfigTest {
    public static void main(String[] args) {
        for(String rank:new String[]{"roamer","tier1","tier2","tier3"}) {
            var config=new FactionEquipmentConfig(new JsonObject(),rank);
            String[] factions=rank.equals("roamer")?new String[]{"roamer"}:new String[]{"crimson","azure","verdant","ashen"};
            for(String faction:factions) {
                var profile=config.profile(faction);
                for(String key:new String[]{"melee_band_weights","armor_band_weights","toughness_band_weights","shield_band_weights","ranged_band_weights",
                        "food_category_weights","recovery_category_weights","tactical_category_weights"}) {
                    int size=key.startsWith("melee")||key.startsWith("armor")?5:key.startsWith("recovery")?3:key.startsWith("tactical")?7:4;
                    check(Math.abs(java.util.Arrays.stream(profile.weights(key,size)).sum()-100)<.001,rank+"/"+faction+"/"+key);
                }
                check(java.util.Arrays.stream(profile.shieldCounts()).sum()==100,"shield count row");
                if(rank.equals("tier3")) check(profile.number("vanishing_chance",99,0,1)==0 && profile.number("binding_chance",99,0,1)==0,"tier3 has no new binding or vanishing");
            }
        }
        JsonObject global=json("{\"bands\":{\"melee_damage\":[3,5,7,9]},\"item_pool\":{\"integrations\":false}}");
        FactionEquipmentConfig.configure(global,Map.of("crimson",json("{\"tier1\":{\"gun_chance\":0.2}}")));
        var config=new FactionEquipmentConfig(json("{\"gun_chance\":0.3,\"faction_overrides\":{\"crimson\":{\"gun_chance\":0.6}}}"),"tier1");
        check(config.profile("crimson").number("gun_chance",0,0,1)==.6,"explicit faction wins");
        check(config.profile("azure").number("gun_chance",0,0,1)==.3,"rank wins over builtins");
        check(config.profile("crimson").bands("melee_damage",4,6,8,11)[0]==3,"global bands inherited");
        check(!config.profile("neutral").factionBands(),"unknown faction uses legacy rank candidates");
        FactionEquipmentConfig.configure(new JsonObject(),Map.of());
        System.out.println("FactionEquipmentConfigTest: passed");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
