package dev.felix.hostilehumans.loot;

import com.google.gson.*;

/** Runs without starting a client/server. Unknown fields must survive every convenience edit. */
public final class LootAuthoringTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void reject(String json){try{LootDocuments.parse(json);throw new AssertionError("accepted unsafe document");}catch(RuntimeException expected){}}
    public static void main(String[] args){
        var original=LootDocuments.parse("{\"type\":\"minecraft:chest\",\"mod:metadata\":{\"opaque\":[1,2,3]},\"pools\":[]}");
        String before=original.toString();var copy=original.deepCopy();LootSimpleDraft.addPool(copy);
        check(original.toString().equals(before),"draft must not mutate source table");
        check(copy.get("mod:metadata").equals(original.get("mod:metadata")),"unknown root fields survive");
        var entry=LootDocuments.parse("{\"type\":\"minecraft:item\",\"name\":\"minecraft:bread\",\"conditions\":[{\"condition\":\"othermod:custom\"}],\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":2},{\"function\":\"othermod:custom\",\"opaque\":true}],\"extension\":[7]}");
        var initial=LootSimpleDraft.item(entry);check(initial!=null&&initial.minimum()==2,"simple count read");
        var changed=LootSimpleDraft.patch(entry,new LootSimpleDraft.Item("minecraft:apple",3,2,6));
        check(changed.get("conditions").equals(entry.get("conditions")),"conditions survive");
        check(changed.get("extension").equals(entry.get("extension")),"opaque fields survive");
        check(changed.getAsJsonArray("functions").get(1).equals(entry.getAsJsonArray("functions").get(1)),"unknown function and order survive");
        check(entry.get("name").getAsString().equals("minecraft:bread"),"original entry unchanged");
        var edited=LootSimpleDraft.item(changed);check(edited!=null&&edited.weight()==3&&edited.maximum()==6,"round trip");
        var complex=entry.deepCopy();complex.getAsJsonArray("functions").get(0).getAsJsonObject().add("count",LootDocuments.parse("{\"type\":\"minecraft:binomial\",\"n\":5,\"p\":0.5}"));
        check(LootSimpleDraft.item(complex)==null,"complex provider cannot silently become uniform");
        var additive=entry.deepCopy();additive.getAsJsonArray("functions").get(0).getAsJsonObject().addProperty("add",true);
        check(LootSimpleDraft.item(additive)==null,"additive count needs advanced editing");
        reject("[]");reject("{\"x\":\""+"a".repeat(65536)+"\"}");
        reject("{\"x\":"+"[".repeat(50)+"0"+"]".repeat(50)+"}");
        reject("{\"x\":["+"0,".repeat(4096)+"0]}");
        String quoted="{\"text\":\""+"[]{}".repeat(100)+"\"}";
        check(LootDocuments.parse(quoted).get("text").getAsString().length()==400,"quoted braces do not count as nesting");
        check(LootDocuments.parse(LootDocuments.JSON.toJson(original)).equals(original),"JSON tree round trip");
        check(LootSimpleDraft.withStackData(changed,"{}").equals(changed),"plain items do not gain a redundant NBT function");
        var withGun=LootSimpleDraft.withStackData(changed,"{GunId:\"tacz:ak47\"}");
        var funcs=withGun.getAsJsonArray("functions");
        check(funcs.get(funcs.size()-1).getAsJsonObject().get("tag").getAsString().contains("tacz:ak47"),"specific gun data preserved");
        var switched=LootSimpleDraft.withStackData(withGun,"{Potion:\"minecraft:healing\"}");
        check(switched.getAsJsonArray("functions").size()==funcs.size(),"repeated selection does not accumulate NBT functions");
        check(switched.get("conditions").equals(entry.get("conditions")),"stack selection preserves original conditions");
        try{LootSimpleDraft.withStackData(changed,"x".repeat(16385));throw new AssertionError("oversized item data accepted");}catch(IllegalArgumentException expected){}
        System.out.println("LootAuthoringTest passed");
    }
}
