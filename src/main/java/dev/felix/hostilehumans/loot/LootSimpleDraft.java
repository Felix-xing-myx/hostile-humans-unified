package dev.felix.hostilehumans.loot;

import com.google.gson.*;

/** Lossless, intentionally narrow conveniences. Complex providers keep their original JSON. */
public final class LootSimpleDraft {
    private LootSimpleDraft() {}
    public record Item(String id, int weight, int minimum, int maximum) {}
    public static JsonArray pools(JsonObject root) {
        return root.has("pools") && root.get("pools").isJsonArray() ? root.getAsJsonArray("pools") : null;
    }
    public static JsonObject addPool(JsonObject root) {
        if (!root.has("pools")) root.add("pools",new JsonArray());
        var pools=pools(root); if(pools==null) throw new IllegalArgumentException("pools_invalid");
        JsonObject pool=new JsonObject();pool.addProperty("rolls",1);pool.add("entries",new JsonArray());pools.add(pool);return pool;
    }
    public static JsonArray entries(JsonObject pool) {
        return pool.has("entries")&&pool.get("entries").isJsonArray()?pool.getAsJsonArray("entries"):null;
    }
    public static Item item(JsonElement element) {
        try {
            var entry=element.getAsJsonObject();
            if(!entry.get("type").getAsString().equals("minecraft:item")) return null;
            int min=1,max=1,counts=0;
            if(entry.has("functions")) for(var f:entry.getAsJsonArray("functions")) {
                var function=f.getAsJsonObject();
                if(!function.get("function").getAsString().equals("minecraft:set_count")) continue;
                if(++counts>1||function.has("add")&&function.get("add").getAsBoolean())return null;
                var count=function.get("count");
                if(count.isJsonPrimitive())min=max=integer(count);
                else {
                    var range=count.getAsJsonObject();
                    if(!range.get("type").getAsString().equals("minecraft:uniform"))return null;
                    min=integer(range.get("min"));max=integer(range.get("max"));
                }
            }
            int weight=entry.has("weight")?integer(entry.get("weight")):1;
            if(weight<1||weight>100000||min<1||max<min||max>64)return null;
            return new Item(entry.get("name").getAsString(),weight,min,max);
        }catch(RuntimeException complex){return null;}
    }
    private static int integer(JsonElement value) {
        double d=value.getAsDouble();int n=value.getAsInt();if(!Double.isFinite(d)||d!=n)throw new IllegalArgumentException("integer");return n;
    }
    public static JsonObject patch(JsonObject original, Item edit) {
        if(edit.id()==null||edit.id().isBlank()||edit.weight()<1||edit.weight()>100000||edit.minimum()<1||edit.maximum()<edit.minimum()||edit.maximum()>64)
            throw new IllegalArgumentException("item_invalid");
        JsonObject entry=original.deepCopy();entry.addProperty("type","minecraft:item");entry.addProperty("name",edit.id());entry.addProperty("weight",edit.weight());
        JsonArray functions=entry.has("functions")?entry.getAsJsonArray("functions"):new JsonArray();
        JsonObject function=null;
        for(var f:functions)if(f.getAsJsonObject().get("function").getAsString().equals("minecraft:set_count")){function=f.getAsJsonObject();break;}
        if(function==null){function=new JsonObject();function.addProperty("function","minecraft:set_count");functions.add(function);}
        JsonObject count=new JsonObject();count.addProperty("type","minecraft:uniform");count.addProperty("min",edit.minimum());count.addProperty("max",edit.maximum());function.add("count",count);entry.add("functions",functions);
        return entry;
    }
    /** Explicit author-selected stack data, expressed as a vanilla loot function. */
    public static JsonObject withStackData(JsonObject original,String snbt) {
        if(snbt==null||snbt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384)
            throw new IllegalArgumentException("stack_data_large");
        var entry=original.deepCopy();
        JsonArray functions=entry.has("functions")?entry.getAsJsonArray("functions"):new JsonArray();
        JsonObject function=null;
        if(!functions.isEmpty()){
            var last=functions.get(functions.size()-1);
            if(last.isJsonObject()){
                var object=last.getAsJsonObject();
                if(object.has("function")&&object.get("function").isJsonPrimitive()
                        &&object.get("function").getAsString().equals("minecraft:set_nbt")&&!object.has("conditions"))function=object;
            }
        }
        if(snbt.trim().equals("{}")){
            if(function==null)return entry;
            if(function.keySet().size()==2&&function.has("tag")){functions.remove(functions.size()-1);entry.add("functions",functions);return entry;}
        }
        if(function==null){function=new JsonObject();function.addProperty("function","minecraft:set_nbt");functions.add(function);}
        function.addProperty("tag",snbt);entry.add("functions",functions);return entry;
    }

}
