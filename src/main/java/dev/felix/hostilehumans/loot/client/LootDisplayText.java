package dev.felix.hostilehumans.loot.client;

import com.google.gson.JsonElement;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Human-readable labels are separate from all persisted keys, types and resource IDs. */
final class LootDisplayText {
    private LootDisplayText(){}
    static Component field(String key){
        String translation="loot_editor.field."+key;
        return I18n.exists(translation)?Component.translatable(translation).append(" ["+key+"]"):Component.literal(key);
    }
    static Component table(String raw){
        var id=ResourceLocation.tryParse(raw);if(id==null)return Component.literal(raw);
        String key="loot_table."+id.getNamespace()+"."+id.getPath().replace('/','.');
        if(I18n.exists(key))return Component.translatable(key).append(" · "+id);
        return Component.literal("["+id.getNamespace()+"] ").append(id.getPath());
    }
    static LootItemCatalog.Entry item(JsonElement node){
        if(!node.isJsonObject())return null;
        var object=node.getAsJsonObject();
        if(!object.has("type")||!object.get("type").isJsonPrimitive()||!object.get("type").getAsString().equals("minecraft:item")
                ||!object.has("name")||!object.get("name").isJsonPrimitive())return null;
        var base=LootItemCatalog.entry(object.get("name").getAsString());if(base==null)return null;
        var stack=base.icon();
        if(object.has("functions")&&object.get("functions").isJsonArray())for(var raw:object.getAsJsonArray("functions")){
            if(!raw.isJsonObject())continue;
            var function=raw.getAsJsonObject();
            if(!function.has("function")||!function.get("function").isJsonPrimitive()
                    ||!function.get("function").getAsString().equals("minecraft:set_nbt")||function.has("conditions")
                    ||!function.has("tag")||!function.get("tag").isJsonPrimitive())continue;
            try{stack.getOrCreateTag().merge(net.minecraft.nbt.TagParser.parseTag(function.get("tag").getAsString()));}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException|RuntimeException ignored){/* Type preview remains valid; native validation owns invalid loot. */}
        }
        return LootItemCatalog.describe(stack);
    }
    static String summary(JsonElement node){
        var item=item(node);if(item!=null)return item.title().getString();
        String text;
        if(node.isJsonObject())text="{ "+String.join(", ",node.getAsJsonObject().keySet().stream().map(k->field(k).getString()).toList())+" }";
        else if(node.isJsonArray())text=Component.translatable("loot_editor.node_count",node.getAsJsonArray().size()).getString();
        else if(node.isJsonPrimitive()&&node.getAsJsonPrimitive().isString()) {
            String raw=node.getAsString(),translation="loot_editor.kind."+raw.replace(':','.');
            text=I18n.exists(translation)?Component.translatable(translation).getString()+" ["+raw+"]":node.toString();
        }else text=node.toString();
        return text.length()>110?text.substring(0,107)+"...":text;
    }
}
