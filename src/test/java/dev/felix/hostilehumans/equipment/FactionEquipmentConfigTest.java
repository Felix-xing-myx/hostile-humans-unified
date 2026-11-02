package dev.felix.hostilehumans.equipment;

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
                for(String key:new String[]{"melee_band_weights","armor_band_weights","shield_band_weights","ranged_band_weights",
                        "recovery_category_weights","self_buff_category_weights","throwable_category_weights"}) {
                    int size=key.startsWith("melee")||key.startsWith("armor")?5:key.startsWith("self_buff")?2:key.startsWith("throwable")?5:4;
                    check(Math.abs(java.util.Arrays.stream(profile.weights(key,size)).sum()-100)<.001,rank+"/"+faction+"/"+key);
                }
                check(java.util.Arrays.stream(profile.shieldCounts()).sum()==100,"shield count row");
                if(rank.equals("tier3")) check(profile.number("vanishing_chance",99,0,1)==0 && profile.number("binding_chance",99,0,1)==0,"tier3 has no new binding or vanishing");
            }
        }
        var rankOnlyArmor=new FactionEquipmentConfig(json("""
                {"faction_overrides":{"crimson":{"armor_band_weights":[100,0,0,0,0],
                  "bands":{"armor_points":[1,2,3,4]}}}}
                """),"tier2").profile("crimson");
        check(java.util.Arrays.equals(rankOnlyArmor.weights("armor_rank_band_weights",5),new double[]{0,0,53,37,10}),
                "faction armor band weights cannot bias rank baseline");
        check(rankOnlyArmor.bands("armor_points",7,11,15,19)[0]==7,"faction armor thresholds cannot bias rank baseline");
        var legacyRecovery=new FactionEquipmentConfig(json("{\"recovery_category_weights\":[50,25,25]}"),"tier1").profile("crimson");
        check(java.util.Arrays.equals(legacyRecovery.weights("recovery_category_weights",4),new double[]{0,50,25,25}),
                "legacy recovery weights retain strong-healing membership");
        var explicitEmpty=new FactionEquipmentConfig(json("{\"shield_archetype_weights\":{},\"equipment_archetypes\":{}}"),"tier1").profile("azure");
        check(FactionEquipmentConfig.object(explicitEmpty.data,"shield_archetype_weights").size()==0,"explicit empty family weights clear builtin doctrine");
        FactionEquipmentConfig.prepare(json("{\"equipment_archetypes\":{\"melee\":{\"dagger\":[\"#example:daggers\",\"example:knife\"]}}}"),Map.of());
        var doctrine=new FactionEquipmentConfig(new JsonObject(),"tier3").profile("azure");
        check(doctrine.number("doctrine_core_chance",0,0,1)==.85,"tier3 non-gun doctrine chance");
        check(FactionEquipmentConfig.object(doctrine.data,"shield_archetype_weights").get("tower").getAsInt()==70,"azure tower preference");
        JsonObject global=json("{\"bands\":{\"melee_damage\":[3,5,7,9]},\"item_pool\":{\"integrations\":false}}");
        FactionEquipmentConfig.configure(global,Map.of("crimson",json("{\"tier1\":{\"gun_chance\":0.2}}")));
        var config=new FactionEquipmentConfig(json("{\"gun_chance\":0.3,\"faction_overrides\":{\"crimson\":{\"gun_chance\":0.6}}}"),"tier1");
        check(config.profile("crimson").number("gun_chance",0,0,1)==.6,"explicit faction wins");
        check(config.profile("azure").number("gun_chance",0,0,1)==.3,"rank wins over builtins");
        check(config.profile("crimson").bands("melee_damage",4,6,8,11)[0]==3,"global bands inherited");
        check(!config.profile("neutral").factionBands(),"unknown faction uses legacy rank candidates");
        var activeConfig=new FactionEquipmentConfig(new JsonObject(),"tier1");
        long revision=FactionEquipmentConfig.revision();
        double activeChance=activeConfig.profile("crimson").number("gun_chance",0,0,1);
        FactionEquipmentConfig.prepare(json("{\"tier1\":{\"gun_chance\":0.8}}"),Map.of());
        check(FactionEquipmentConfig.revision()==revision,"successful prepare does not publish or advance effective revision");
        for(String malformed:new String[]{
                "{\"tier1\":{\"gun_chance\":1.1}}",
                "{\"tier1\":{\"faction_overrides\":{\"crimson\":{\"armor_band_weights\":[1,2,3]}}}}",
                "{\"tier2\":{\"melee_band_weights\":[1,2,-1,3,4]}}",
                "{\"bands\":{\"shield_durability\":[200,200,1000]}}",
                "{\"item_pool\":{\"extra\":[\"Bad Id\"]}}",
                "{\"tier3\":{\"enchant\":{\"cost_overrides\":{\"minecraft:thorns\":0}}}}",
                "{\"tier1\":{\"gun_category_map\":{\"scguns:longarm\":\"typo\"}}}",
                "{\"tier1\":{\"durability\":{\"min\":0.8,\"max\":0.2}}}",
                "{\"tier1\":{\"mainhand\":[{\"item\":\"Bad:item\"}]}}",
                "{\"tier1\":{\"enchant\":{\"blacklist\":[12]}}}",
                "{\"tier1\":{\"gun_chance\":-1,\"faction_overrides\":{\"crimson\":{\"gun_chance\":0.5}}}}"
        }) expectRejected(json(malformed),Map.of(),activeConfig,revision,activeChance);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            JsonObject root=new JsonObject(),rank=new JsonObject(); rank.addProperty("gun_chance",invalid); root.add("tier1",rank);
            expectRejected(root,Map.of(),activeConfig,revision,activeChance);
        }
        expectRejected(new JsonObject(),Map.of("azure",json("{\"tier2\":{\"gun_core_chance\":-0.01}}")),activeConfig,revision,activeChance);
        expectRejected(new JsonObject(),Map.of("Bad Id",new JsonObject()),activeConfig,revision,activeChance);
        var denyConfig=new FactionEquipmentConfig(json("{\"item_pool\":{\"deny\":[\"minecraft:shield\"]}}"),"tier1").profile("crimson");
        String original="spartanshields:iron_tower_shield";
        String grown=FactionEquipmentPolicy.allowedReplacement(original,"minecraft:shield",
                id->denyConfig.allowedItem(net.minecraft.resources.ResourceLocation.tryParse(id),false));
        check(grown.equals(original),"denied vanilla shield fallback cannot return through natural growth");
        var explicitConfig=new FactionEquipmentConfig(json("{\"item_pool\":{\"integrations\":false,\"extra\":[\"example:shield\"],\"deny\":[\"example:denied\"]}}"),"tier1").profile("crimson");
        check(explicitConfig.allowedItem(net.minecraft.resources.ResourceLocation.tryParse("example:shield"),false),"growth recognizes explicitly added optional item");
        check(!explicitConfig.allowedItem(net.minecraft.resources.ResourceLocation.tryParse("example:other"),false),"disabled integrations exclude implicit optional fallback");
        var prepared=FactionEquipmentConfig.prepare(json("{\"tier1\":{\"gun_chance\":0.4}}"),Map.of());
        FactionEquipmentConfig.publish(prepared);
        check(FactionEquipmentConfig.revision()>revision,"publish advances effective revision exactly after valid preparation");
        JsonObject extensible=json("""
                {"bands":{"melee_damage":[3,5,7,9]},"tier1":{"faction_overrides":{
                  "expansion:scouts":{"enchant":{"weapon_chance":0.45}},
                  "new_recruits":{"melee_band_weights":[100,0,0,0,0]}}}}
                """);
        var expanded=FactionEquipmentConfig.prepare(extensible,Map.of(
                "expansion:scouts",new JsonObject(),"new_recruits",new JsonObject(),
                "expansion:raiders",json("{\"tier1\":{\"bands\":{\"melee_damage\":[2,4,6,8]},\"gun_chance\":0.23}}")));
        check(FactionEquipmentConfig.definedKey("expansion:scouts",expanded).equals("expansion:scouts"),"namespaced defined identity preserved");
        check(FactionEquipmentConfig.definedKey("missing:scouts",expanded).equals("neutral"),"undefined identity safely neutral");
        check(FactionEquipmentConfig.definedKey("crimson",expanded).equals("neutral"),"removed builtin definition is not a defined live identity");
        FactionEquipmentConfig.publish(expanded);
        var expandedConfig=new FactionEquipmentConfig(FactionEquipmentConfig.object(extensible,"tier1"),"tier1");
        var scouts=expandedConfig.profile("expansion:scouts");
        check(!scouts.factionBands(),"global bands and explicit enchant-only override preserve neutral candidate semantics");
        check(scouts.bands("melee_damage",4,6,8,11)[0]==3,"global threshold values still inherit without activating faction bands");
        check(FactionEquipmentConfig.number(FactionEquipmentConfig.object(scouts.data,"enchant"),"weapon_chance",0,0,1)==.45,"namespaced override applies above neutral defaults");
        check(expandedConfig.profile("new_recruits").factionBands(),"explicit short-ID band weights activate faction bands");
        check(expandedConfig.profile("expansion:raiders").factionBands(),"explicit namespaced definition bands activate faction bands");
        check(expandedConfig.profile("expansion:raiders").number("gun_chance",0,0,1)==.23,"namespaced definition defaults resolve");
        check(expandedConfig.profile("missing:scouts").faction.equals("neutral"),"unknown profile resolves to neutral");
        boolean overrideRejected=false;
        try { FactionEquipmentConfig.prepare(json("{\"tier2\":{\"faction_overrides\":{\"newspace:unregistered\":{\"armor_band_weights\":[1,-1,0,0,0]}}}}"),Map.of()); }
        catch(IllegalArgumentException expected) { overrideRejected=true; }
        check(overrideRejected,"union validation checks explicit override-only faction even without definition");
        FactionEquipmentConfig.configure(new JsonObject(),Map.of());
        System.out.println("FactionEquipmentConfigTest: passed");
    }
    private static void expectRejected(JsonObject root,Map<String,JsonObject> definitions,FactionEquipmentConfig config,long revision,double chance) {
        boolean rejected=false;
        try { FactionEquipmentConfig.prepare(root,definitions); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"invalid candidate rejected before publication: "+root);
        check(FactionEquipmentConfig.revision()==revision,"failed prepare keeps effective revision");
        check(config.profile("crimson").number("gun_chance",0,0,1)==chance,"failed prepare keeps active equipment snapshot");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
